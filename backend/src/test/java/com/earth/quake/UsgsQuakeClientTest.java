package com.earth.quake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsgsQuakeClientTest {

    // 실제 피드(4.5_day.geojson, 2026-11-17) 형식을 줄인 것.
    private static final String FEED = """
            {"type":"FeatureCollection","metadata":{"count":2},"features":[
              {"type":"Feature","id":"us7000tysq","geometry":{"type":"Point","coordinates":[179.8423,-32.3057,337.077]},
               "properties":{"mag":5.2,"place":"south of the Kermadec Islands","time":1794925236814,"updated":1794926421040,
                             "type":"earthquake","status":"reviewed","title":"M 5.2 - south of the Kermadec Islands"}},
              {"type":"Feature","id":"uu1","geometry":{"type":"Point","coordinates":[-112.0,40.5,0.0]},
               "properties":{"mag":4.6,"place":"Utah","time":1794925000000,"updated":1794925100000,"type":"quarry blast"}}
            ]}""";

    @Test
    @DisplayName("지진만 읽고 값은 원문 그대로, 출처는 USGS")
    void parses() {
        List<Quake> quakes = UsgsQuakeClient.parse(FEED.getBytes(StandardCharsets.UTF_8));
        assertThat(quakes).singleElement().satisfies(q -> {
            assertThat(q.id()).isEqualTo("usgs-us7000tysq");
            assertThat(q.latitude()).isEqualTo(-32.3057);
            assertThat(q.longitude()).isEqualTo(179.8423);
            assertThat(q.depthKm()).isEqualTo(337.077);
            assertThat(q.magnitude()).isEqualTo(5.2);
            assertThat(q.location()).isEqualTo("south of the Kermadec Islands");
            assertThat(q.occurredAt()).isEqualTo(Instant.ofEpochMilli(1794925236814L));
            assertThat(q.source()).isEqualTo(Quake.USGS);
        });
    }
}
