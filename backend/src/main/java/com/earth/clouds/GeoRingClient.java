package com.earth.clouds;

import com.earth.config.CloudProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 위성 영상을 WMS(GetMap)로 받는다. 두 서버 모두 인증이 없다.
 *
 * <p>아직 없는 시각을 물으면 두 서버가 다르게 답한다(2026-10-31 확인). GIBS는 빈(투명) PNG를 주고,
 * EUMETView는 HTTP 200에 XML 예외 문서를 준다. 있는 시각이면 EUMETView는 실제로 쓴 시각을 {@code Warning}
 * 헤더에 적어 준다("99 Nearest value used: time=..."). 빈 영상 판정은 {@link GeoRingFrames}가 한다.
 */
@Component
public class GeoRingClient implements GeoRingFrames.Fetcher {

    private static final Pattern DOMAIN = Pattern.compile("<Domain>([^<]*)</Domain>");
    private static final Pattern WARNING_TIME = Pattern.compile("time=(\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z)");
    // 실제 영상은 2048×1024에서 1.4MB 이하. 이상하게 큰 응답으로 메모리를 채우지 않게 상한을 둔다.
    private static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    private final CloudProperties properties;
    private final RestClient rest;

    public GeoRingClient(CloudProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<GeoRingFrames.Fetched> fetch(GeoSatellite satellite, Instant time, int width) {
        String base = satellite.provider() == GeoSatellite.Provider.GIBS
                ? properties.gibsWmsUrl() : properties.eumetviewWmsUrl();
        String url = base + "?SERVICE=WMS&REQUEST=GetMap&VERSION=1.3.0&CRS=EPSG:4326&BBOX=-90,-180,90,180"
                + "&FORMAT=image/png&WIDTH=" + width + "&HEIGHT=" + width / 2
                + "&LAYERS=" + encode(satellite.layer()) + "&STYLES=" + encode(satellite.style())
                + "&TIME=" + DateTimeFormatter.ISO_INSTANT.format(time.truncatedTo(ChronoUnit.SECONDS));
        return rest.get().uri(URI.create(url)).exchange((request, response) -> {
            String type = response.getHeaders().getContentType() == null
                    ? "" : response.getHeaders().getContentType().toString();
            if (!response.getStatusCode().is2xxSuccessful() || !type.startsWith("image/png")) {
                return Optional.empty();
            }
            Instant observedAt = actualTime(response.getHeaders().getFirst("Warning")).orElse(time);
            BufferedImage image;
            try (InputStream in = response.getBody()) {
                image = ImageIO.read(new ByteArrayInputStream(readWithLimit(in)));
            }
            if (image == null || image.getWidth() != width || image.getHeight() != width / 2) {
                return Optional.empty();
            }
            int[] argb = image.getRGB(0, 0, width, width / 2, null, 0, width);
            return Optional.of(new GeoRingFrames.Fetched(argb, observedAt));
        });
    }

    /** WMTS DescribeDomains. 예: {@code 2026-10-31T03:00:00Z/2026-10-31T04:30:00Z/PT10M} (쉼표로 여러 구간). */
    @Override
    public java.util.List<Instant> availableTimes(GeoSatellite satellite, Instant from, Instant to) {
        String range = DateTimeFormatter.ISO_INSTANT.format(from.truncatedTo(ChronoUnit.MINUTES)) + "--"
                + DateTimeFormatter.ISO_INSTANT.format(to.truncatedTo(ChronoUnit.MINUTES));
        String xml = rest.get()
                .uri(URI.create(properties.gibsDomainsUrl() + "/" + encode(satellite.layer()) + "/default/2km/all/" + range + ".xml"))
                .retrieve()
                .body(String.class);
        return parseDomain(xml);
    }

    static java.util.List<Instant> parseDomain(String xml) {
        java.util.List<Instant> times = new java.util.ArrayList<>();
        Matcher m = xml == null ? null : DOMAIN.matcher(xml);
        if (m == null || !m.find()) {
            return times;
        }
        for (String part : m.group(1).split(",")) {
            String[] pieces = part.trim().split("/");
            if (pieces[0].isEmpty()) {
                continue;
            }
            Instant start = Instant.parse(pieces[0]);
            if (pieces.length < 3) {
                times.add(start);
                continue;
            }
            Instant end = Instant.parse(pieces[1]);
            Duration step = Duration.parse(pieces[2]);
            // 이상한 응답으로 끝없이 돌지 않게 간격과 개수를 제한한다.
            if (step.isZero() || step.isNegative()) {
                continue;
            }
            for (Instant t = start; !t.isAfter(end) && times.size() < 10_000; t = t.plus(step)) {
                times.add(t);
            }
        }
        return times;
    }

    @Override
    public String colormapXml() {
        String xml = rest.get().uri(URI.create(properties.gibsColormapUrl())).retrieve().body(String.class);
        if (xml == null || xml.length() > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("GIBS 색상표를 받지 못했습니다.");
        }
        return xml;
    }

    static Optional<Instant> actualTime(String warning) {
        if (warning == null) {
            return Optional.empty();
        }
        Matcher m = WARNING_TIME.matcher(warning);
        return m.find() ? Optional.of(Instant.parse(m.group(1))) : Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] readWithLimit(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > MAX_IMAGE_BYTES) {
                throw new UncheckedIOException(new IOException("위성 영상이 상한(" + MAX_IMAGE_BYTES + "바이트)을 넘습니다."));
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
