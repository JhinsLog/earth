package com.earth.quake;

import com.earth.config.KmaQuakeProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상청 지진통보문(getEqkMsg)을 받는다. 국내·국외 지진이 함께 온다.
 *
 * <p>국외 지진은 기상청 통보 기준(동아시아 규모 5.0 이상, 그 밖은 6.0 이상)에 드는 것만 온다. 작은 국외 지진이 빠지는 것은
 * 한반도 밖의 작은 지진은 USGS 피드가 채운다(한반도 인근은 기상청만, 같은 지진이면 기상청 — D-18).
 *
 * <p><b>인증키가 쿼리 문자열에 들어간다.</b> RestClient 예외 메시지에는 요청 주소가 그대로 실리므로 예외를 그대로 던지거나
 * 로그로 남기지 않고 키를 지운 메시지로 바꿔 던진다(SeoulIncidentClient와 같은 이유).
 */
@Component
public class KmaQuakeClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter EQK_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter FC_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // 며칠치 통보는 평소 수 건~수십 건이다. 응답이 이상하게 커서 메모리를 채우지 않게 막는다.
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 5;
    /**
     * 같은 지진에 통보가 여러 번 나온다(조기경보 → 지진정보 → 재통보). 진앙시가 이만큼 안이고 진앙이 아래 거리 안이면 같은
     * 지진으로 본다. 조기경보는 자동 분석이라 진앙시·위치가 정밀 분석과 수 초·수 km 어긋난다.
     */
    private static final Duration SAME_QUAKE_WINDOW = Duration.ofMinutes(2);
    private static final double SAME_QUAKE_KM = 100;
    // 인증 오류는 errMsg가 "SERVICE ERROR"뿐이고 이유는 returnAuthMsg에 있다. 그래서 returnAuthMsg를 먼저 본다.
    private static final Pattern AUTH_MSG = Pattern.compile("<returnAuthMsg>([^<]*)</returnAuthMsg>");
    private static final Pattern ERR_MSG = Pattern.compile("<errMsg>([^<]*)</errMsg>");

    private final KmaQuakeProperties properties;
    private final RestClient rest;

    public KmaQuakeClient(KmaQuakeProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 진앙시가 최근 {@code displayHours}시간 안인 지진을, 지진 하나에 하나씩 남겨 진앙시 최신순으로 돌려준다.
     *
     * <p>API는 발표 시각을 날짜(한국)로만 거를 수 있다. 그래서 기간의 시작이 걸친 날부터 오늘까지 받고, 시각은 여기서 자른다.
     * 날짜로만 자르면 지진마다 보이는 시간이 달라지고(밤늦게 난 지진은 짧다) 자정마다 여러 개가 한꺼번에 사라졌다.
     */
    public List<Quake> fetch(Instant now) {
        Duration window = Duration.ofHours(properties.displayHours());
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        LocalDate from = now.minus(window).atZone(SEOUL).toLocalDate();
        List<Quake> notices = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Page result = parse(get(from, today, page));
            notices.addAll(result.quakes());
            if (result.rows() == 0 || page * PAGE_SIZE >= result.total()) {
                break;
            }
        }
        return recent(merge(notices), now, window);
    }

    /** 진앙시가 {@code now - window} 이후인 것만. 진앙시가 앞날 자정 직전이고 발표가 다음 날인 지진도 진앙시로 판단한다. */
    static List<Quake> recent(List<Quake> quakes, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        return quakes.stream().filter(q -> q.occurredAt().isAfter(cutoff)).toList();
    }

    private byte[] get(LocalDate from, LocalDate to, int page) {
        // encode()를 먼저 걸면 {변수}로 넣은 값의 예약 문자(+, /, =)까지 엄격하게 인코딩된다. 인증키에 이 문자가 들어 있다.
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/getEqkMsg")
                .queryParam("serviceKey", "{key}")
                .queryParam("pageNo", page)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("dataType", "JSON")
                .queryParam("fromTmFc", from.format(DAY))
                .queryParam("toTmFc", to.format(DAY))
                .encode()
                .buildAndExpand(properties.apiKey())
                .toUri();
        try {
            byte[] body = rest.get().uri(uri).retrieve().body(byte[].class);
            return body == null ? new byte[0] : body;
        } catch (RuntimeException e) {
            throw new IllegalStateException("기상청 지진정보 요청 실패: " + e.getClass().getSimpleName() + " "
                    + redact(e.getMessage()));
        }
    }

    private String redact(String message) {
        if (message == null) return "";
        String key = properties.apiKey();
        if (key == null || key.isBlank()) return message;
        String encoded = java.net.URLEncoder.encode(key, StandardCharsets.UTF_8);
        return message.replace(key, "***").replace(encoded, "***");
    }

    /** @param rows 이번 페이지에서 받은 항목 수(좌표가 깨져 건너뛴 것 포함). @param total 전체 건수. */
    record Page(int total, int rows, List<Quake> quakes) {
    }

    /**
     * 응답 JSON을 읽는다. 정상은 resultCode {@code 00}, 기간 안에 통보가 없으면 {@code 03}(NO_DATA)이다.
     *
     * <p>인증키 오류는 JSON을 요청해도 공공데이터포털 게이트웨이가 XML로 답한다(OpenAPI_ServiceResponse). 그 메시지를
     * 꺼내 예외로 바꾼다.
     */
    static Page parse(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8).strip();
        if (text.startsWith("<")) {
            Matcher auth = AUTH_MSG.matcher(text);
            Matcher err = ERR_MSG.matcher(text);
            String reason = auth.find() ? auth.group(1) : err.find() ? err.group(1) : text.substring(0, Math.min(200, text.length()));
            throw new IllegalStateException("기상청 지진정보 응답 오류: " + reason);
        }
        JsonNode response = JSON.readTree(text).path("response");
        String code = response.path("header").path("resultCode").asString("");
        if ("03".equals(code)) {
            return new Page(0, 0, List.of());
        }
        if (!"00".equals(code)) {
            throw new IllegalStateException("기상청 지진정보 응답 오류: " + code + " "
                    + response.path("header").path("resultMsg").asString(""));
        }
        JsonNode bodyNode = response.path("body");
        JsonNode items = bodyNode.path("items").path("item");
        // 항목이 하나뿐이면 배열이 아니라 객체로 오는 공공데이터포털 API가 있어 둘 다 받는다.
        List<JsonNode> rows = items.isArray() ? List.copyOf(items.values()) : items.isObject() ? List.of(items) : List.of();
        List<Quake> quakes = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            Quake quake = toQuake(row);
            if (quake != null) quakes.add(quake);
        }
        int total = (int) number(bodyNode.path("totalCount"), rows.size());
        return new Page(total, rows.size(), quakes);
    }

    /** 진앙시나 좌표가 없거나 범위를 벗어난 항목은 지도에 찍을 수 없어 건너뛴다(null). */
    static Quake toQuake(JsonNode row) {
        Instant occurredAt = time(str(row, "tmEqk"), EQK_TIME, 14);
        Double lat = decimal(str(row, "lat"));
        Double lon = decimal(str(row, "lon"));
        if (occurredAt == null || lat == null || lon == null || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return null;
        }
        Instant announcedAt = time(str(row, "tmFc"), FC_TIME, 12);
        String id = "kma-%s-%.2f-%.2f".formatted(occurredAt.getEpochSecond(), lat, lon);
        return new Quake(id, occurredAt, announcedAt == null ? occurredAt : announcedAt, lat, lon,
                decimal(str(row, "mt")), decimal(str(row, "dep")),
                str(row, "inT"), str(row, "loc"), str(row, "rem"),
                (int) number(row.path("fcTp"), 0), Quake.KMA);
    }

    /**
     * 같은 지진의 통보를 하나로 줄인다. 가장 늦게 발표된 통보가 가장 정확하므로(조기경보 뒤의 정밀 분석, 수정 통보) 그것을 남긴다.
     * 결과는 진앙시 최신순.
     */
    static List<Quake> merge(List<Quake> notices) {
        List<Quake> byAnnouncedDesc = new ArrayList<>(notices);
        byAnnouncedDesc.sort(Comparator.comparing(Quake::announcedAt).reversed());
        List<Quake> kept = new ArrayList<>();
        for (Quake notice : byAnnouncedDesc) {
            boolean duplicate = kept.stream().anyMatch(k -> sameQuake(k, notice));
            if (!duplicate) kept.add(notice);
        }
        kept.sort(Comparator.comparing(Quake::occurredAt).reversed());
        return List.copyOf(kept);
    }

    private static boolean sameQuake(Quake a, Quake b) {
        Duration gap = Duration.between(a.occurredAt(), b.occurredAt()).abs();
        return gap.compareTo(SAME_QUAKE_WINDOW) <= 0
                && distanceKm(a.latitude(), a.longitude(), b.latitude(), b.longitude()) <= SAME_QUAKE_KM;
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** 숫자 항목이 숫자로도, 문자열로도 오는 API라 문자열로 읽어 다룬다. 없거나 null이면 빈 문자열. */
    private static String str(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isMissingNode() || node.isNull() ? "" : node.asString("").strip();
    }

    private static Double decimal(String value) {
        if (value.isEmpty()) return null;
        try {
            double d = Double.parseDouble(value);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long number(JsonNode node, long fallback) {
        String value = node.isMissingNode() || node.isNull() ? "" : node.asString("").strip();
        try {
            return value.isEmpty() ? fallback : (long) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 한국 시각 문자열. 길이가 다르면(예: 진앙시에 초 미만이 붙은 경우) 앞 {@code length}자리만 읽는다. */
    private static Instant time(String value, DateTimeFormatter format, int length) {
        if (value.length() < length) return null;
        try {
            return LocalDateTime.parse(value.substring(0, length), format).atZone(SEOUL).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
