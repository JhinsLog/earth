package com.earth.clouds;

import com.earth.config.CloudProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOAA GMGSI 공개 버킷(AWS S3)에서 최신 파일을 찾아 받는다. 인증이 없고 S3 REST 목록 API만 쓴다.
 *
 * <p>파일은 매시 정각 관측분이 약 35분 뒤에 올라온다. 그래서 지금 시각과 그 앞 두 시간의 폴더를 본다.
 */
@Component
public class GmgsiClient {

    private static final DateTimeFormatter HOUR_FOLDER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/").withZone(ZoneOffset.UTC);
    private static final Pattern KEY = Pattern.compile("<Key>([^<]+)</Key>");
    // 목록에서 온 키를 그대로 URL에 붙이므로 모양을 좁혀 둔다(경로 조작·다른 호스트로 새는 것 방지).
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_]+(/[A-Za-z0-9_.]+)+\\.nc");
    // 실제 파일은 약 7MB. 이상하게 큰 응답으로 디스크를 채우지 않게 상한을 둔다.
    private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;

    private final CloudProperties properties;
    private final RestClient rest;

    public GmgsiClient(CloudProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    /** 가장 최근 파일의 키. 세 시간 안에 하나도 없으면 비어 있다. */
    public Optional<String> latestKey(Instant now) {
        for (int hoursAgo = 0; hoursAgo <= 2; hoursAgo++) {
            String prefix = properties.product() + "/" + HOUR_FOLDER.format(now.minus(hoursAgo, ChronoUnit.HOURS));
            String listing = rest.get()
                    .uri(URI.create(properties.bucketUrl() + "/?list-type=2&prefix=" + prefix))
                    .retrieve()
                    .body(String.class);
            Optional<String> newest = keys(listing).max(Comparator.naturalOrder());
            if (newest.isPresent()) {
                return newest;
            }
        }
        return Optional.empty();
    }

    /** 임시 파일로 받는다. 지우는 것은 호출한 쪽 책임. */
    public Path download(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("허용하지 않는 키 모양: " + key);
        }
        try {
            Path file = Files.createTempFile("gmgsi-", ".nc");
            try {
                rest.get()
                        .uri(URI.create(properties.bucketUrl() + "/" + key))
                        .exchange((request, response) -> {
                            if (!response.getStatusCode().is2xxSuccessful()) {
                                throw new IOException("GMGSI 다운로드 실패: " + response.getStatusCode());
                            }
                            try (InputStream in = response.getBody(); OutputStream out = Files.newOutputStream(file)) {
                                copyWithLimit(in, out);
                            }
                            return null;
                        });
                return file;
            } catch (RuntimeException e) {
                Files.deleteIfExists(file);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static java.util.stream.Stream<String> keys(String listing) {
        if (listing == null) {
            return java.util.stream.Stream.empty();
        }
        Matcher m = KEY.matcher(listing);
        java.util.stream.Stream.Builder<String> keys = java.util.stream.Stream.builder();
        while (m.find()) {
            if (m.group(1).endsWith(".nc")) {
                keys.add(m.group(1));
            }
        }
        return keys.build();
    }

    private static void copyWithLimit(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_FILE_BYTES) {
                throw new IOException("GMGSI 파일이 상한(" + MAX_FILE_BYTES + "바이트)을 넘습니다.");
            }
            out.write(buffer, 0, n);
        }
    }
}
