package com.earth.quake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuakeSourcesTest {

    private static final Instant T = Instant.parse("2026-11-18T03:00:00Z");

    private static Quake kma(String id, double lat, double lon, Instant at) {
        return new Quake(id, at, at, lat, lon, 5.1, null, "", "기상청 원문", "", 2, Quake.KMA);
    }

    private static Quake usgs(String id, double lat, double lon, Instant at) {
        return new Quake(id, at, at, lat, lon, 5.0, 10.0, "", "USGS place", "", 0, Quake.USGS);
    }

    @Test
    @DisplayName("한반도 인근(북위 32~41, 동경 122~132)의 해외 기관 지진은 기상청 통보가 없어도 쓰지 않는다")
    void nearKoreaIsKmaOnly() {
        List<Quake> result = QuakeSources.combine(List.of(), List.of(
                usgs("usgs-gyeongju", 35.77, 129.19, T),
                usgs("usgs-pyongyang", 39.0, 125.7, T),
                usgs("usgs-toyama", 37.3, 137.0, T)));
        assertThat(result).extracting(Quake::id).containsExactly("usgs-toyama");
    }

    @Test
    @DisplayName("기상청이 같은 지진(2분·100km 안)을 통보했으면 기상청 것만 남긴다")
    void sameQuakePrefersKma() {
        List<Quake> result = QuakeSources.combine(
                List.of(kma("kma-toyama", 37.3, 137.0, T)),
                List.of(usgs("usgs-toyama", 37.5, 137.2, T.plusSeconds(40)),
                        usgs("usgs-chile", -33.4, -70.6, T.minusSeconds(3600))));
        assertThat(result).extracting(Quake::id).containsExactly("kma-toyama", "usgs-chile");
        assertThat(result).extracting(Quake::source).containsExactly(Quake.KMA, Quake.USGS);
    }

    @Test
    @DisplayName("시각이나 거리가 기준을 넘으면 다른 지진으로 보고 둘 다 남긴다")
    void differentQuakesKept() {
        List<Quake> result = QuakeSources.combine(
                List.of(kma("kma-toyama", 37.3, 137.0, T)),
                List.of(usgs("usgs-later", 37.3, 137.0, T.plusSeconds(600)),
                        usgs("usgs-far", 34.0, 140.5, T)));
        assertThat(result).hasSize(3);
    }
}
