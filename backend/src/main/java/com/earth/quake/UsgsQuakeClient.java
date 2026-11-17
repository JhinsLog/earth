package com.earth.quake;

import com.earth.config.UsgsQuakeProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * USGS 지진 GeoJSON 피드를 읽는다. 값은 USGS 원문 그대로다 — 위치 설명도 영어 원문(예: "south of the Kermadec Islands")이고,
 * 우리가 번역하거나 다시 쓰지 않는다. 지진이 아닌 사건(채석 발파 등, type이 earthquake가 아님)은 넣지 않는다.
 */
@Component
public class UsgsQuakeClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UsgsQuakeProperties properties;
    private final RestClient rest;

    public UsgsQuakeClient(UsgsQuakeProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public List<Quake> fetch() {
        byte[] body;
        try {
            body = rest.get().uri(URI.create(properties.feedUrl())).retrieve().body(byte[].class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("USGS 지진 피드 요청 실패: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
        return parse(body == null ? new byte[0] : body);
    }

    static List<Quake> parse(byte[] body) {
        JsonNode root = JSON.readTree(body);
        List<Quake> quakes = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            JsonNode p = feature.path("properties");
            JsonNode c = feature.path("geometry").path("coordinates");
            if (!"earthquake".equals(p.path("type").asString("")) || c.size() < 2 || !p.path("time").isNumber()) continue;
            Instant occurredAt = Instant.ofEpochMilli(p.path("time").asLong());
            Instant updatedAt = p.path("updated").isNumber() ? Instant.ofEpochMilli(p.path("updated").asLong()) : occurredAt;
            quakes.add(new Quake("usgs-" + feature.path("id").asString(""), occurredAt, updatedAt,
                    c.get(1).asDouble(), c.get(0).asDouble(),
                    p.path("mag").isNumber() ? p.path("mag").asDouble() : null,
                    c.size() > 2 && c.get(2).isNumber() ? c.get(2).asDouble() : null,
                    "", p.path("place").asString(""), "", 0, Quake.USGS));
        }
        return quakes;
    }
}
