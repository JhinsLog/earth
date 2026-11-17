package com.earth.quake;

import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuakeApiTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    QuakeStore store;

    @AfterEach
    void tearDown() {
        store.replace(List.of(), null);
        store.replaceOverseas(List.of(), null);
    }

    @Test
    @DisplayName("로그인 없이 받을 수 있고, 출처(기상청)와 통보문 값을 그대로 주며 30초 캐시한다")
    void anonymousCanRead() throws Exception {
        Instant fetchedAt = Instant.parse("2026-11-16T01:20:00Z");
        store.replace(List.of(new Quake("kma-1", Instant.parse("2026-11-16T01:15:00Z"), Instant.parse("2026-11-16T01:19:00Z"),
                35.77, 129.19, 3.1, null, "최대진도 Ⅲ(경북)", "경북 경주시 남남서쪽 9km 지역", "안전에 유의하기 바랍니다", 3, Quake.KMA)),
                fetchedAt);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/globe/quakes")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("max-age=30"));
        assertThat(response.body())
                .contains("\"source\":\"기상청\"")
                .contains("\"fetchedAt\":\"2026-11-16T01:20:00Z\"")
                .contains("\"magnitude\":3.1")
                .contains("경북 경주시 남남서쪽 9km 지역")
                .doesNotContain("noticeType");
    }

    @Test
    @DisplayName("해외 기관 지진은 한반도 인근이면 빼고, 먼 곳은 출처와 함께 더한다")
    void overseasQuakesAreMergedWithKmaPriority() throws Exception {
        store.replace(List.of(), Instant.parse("2026-11-16T01:20:00Z"));
        store.replaceOverseas(List.of(
                new Quake("usgs-near", Instant.parse("2026-11-16T01:00:00Z"), Instant.parse("2026-11-16T01:05:00Z"),
                        36.0, 129.5, 4.6, 10.0, "", "near Korea", "", 0, Quake.USGS),
                new Quake("usgs-far", Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-16T00:10:00Z"),
                        -32.3, 179.8, 5.2, 337.0, "", "south of the Kermadec Islands", "", 0, Quake.USGS)),
                Instant.parse("2026-11-16T01:20:00Z"));

        String body = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/globe/quakes")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();

        assertThat(body).contains("usgs-far").contains("\"source\":\"USGS\"").doesNotContain("usgs-near");
    }
}
