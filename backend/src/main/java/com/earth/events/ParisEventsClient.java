package com.earth.events;

import com.earth.config.ParisEventsProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「Que faire à Paris ?」에서 오늘·내일 사이에 열리는 행사를 받는다. 인증키가 필요 없다.
 *
 * <p>목록 API는 한 번에 100건이라 내보내기(exports/json)로 한 번에 받는다. 진행 중인 행사가 1,600건 안팎이고(2026-11-18),
 * 필요한 열만 고른다({@code select}). 제목·설명·연락처는 고르지 않는다 — 받지 않은 것은 새어 나갈 수도 없다.
 */
@Component
public class ParisEventsClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SELECT = "id,address_name,lat_lon,qfap_tags,occurrences";
    // 오늘 회차를 고르려면 오늘까지 시작했고 아직 안 끝난 행사면 된다. 시간대 차이를 넉넉히 덮으려고 하루 더 본다.
    private static final String WHERE = "date_end >= now() AND date_start <= now(days=1) AND lat_lon is not null";

    private final ParisEventsProperties properties;
    private final RestClient rest;

    public ParisEventsClient(ParisEventsProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public List<ParisEvent> fetch() {
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/exports/json")
                .queryParam("select", "{select}")
                .queryParam("where", "{where}")
                .encode()
                .buildAndExpand(SELECT, WHERE)
                .toUri();
        byte[] body;
        try {
            body = rest.get().uri(uri).retrieve().body(byte[].class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("파리 행사 요청 실패: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
        return parse(body == null ? new byte[0] : body);
    }

    static List<ParisEvent> parse(byte[] body) {
        JsonNode root = JSON.readTree(body);
        if (!root.isArray()) {
            throw new IllegalStateException("파리 행사 응답이 목록이 아닙니다.");
        }
        List<ParisEvent> events = new ArrayList<>();
        for (JsonNode node : root) {
            JsonNode point = node.path("lat_lon");
            String venue = node.path("address_name").asString("").strip();
            if (!point.isObject() || venue.isEmpty()) continue;
            List<String> tags = Arrays.stream(node.path("qfap_tags").asString("").split(";"))
                    .map(String::strip).filter(t -> !t.isEmpty()).toList();
            events.add(new ParisEvent(node.path("id").asString(""), venue, point.path("lat").asDouble(),
                    point.path("lon").asDouble(), tags, occurrences(node.path("occurrences").asString(""))));
        }
        return events;
    }

    /** "시작_끝;시작_끝;…"(ISO-8601, 오프셋 포함). 읽을 수 없는 조각은 건너뛴다. */
    static List<ParisEvent.Occurrence> occurrences(String raw) {
        List<ParisEvent.Occurrence> result = new ArrayList<>();
        for (String part : raw.split(";")) {
            String[] range = part.strip().split("_");
            if (range.length != 2) continue;
            try {
                result.add(new ParisEvent.Occurrence(OffsetDateTime.parse(range[0]), OffsetDateTime.parse(range[1])));
            } catch (DateTimeParseException ignored) {
                // 원본에 형식이 다른 회차가 섞여도 나머지는 쓴다.
            }
        }
        return result;
    }
}
