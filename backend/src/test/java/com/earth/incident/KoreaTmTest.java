package com.earth.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KoreaTmTest {

    @Test
    @DisplayName("원점(200000, 500000)은 북위 38°·동경 127°")
    void origin() {
        double[] p = KoreaTm.toLatLng(200_000, 500_000);
        assertThat(p[0]).isCloseTo(38.0, within(1e-9));
        assertThat(p[1]).isCloseTo(127.0, within(1e-9));
    }

    @Test
    @DisplayName("정변환 → 역변환이 서울 범위에서 1cm 안으로 되돌아온다")
    void roundTrip() {
        for (double lat = 37.4; lat <= 37.7; lat += 0.05) {
            for (double lng = 126.8; lng <= 127.2; lng += 0.05) {
                double[] xy = KoreaTm.fromLatLng(lat, lng);
                double[] back = KoreaTm.toLatLng(xy[0], xy[1]);
                assertThat(back[0]).isCloseTo(lat, within(1e-7));
                assertThat(back[1]).isCloseTo(lng, within(1e-7));
            }
        }
    }

    @Test
    @DisplayName("실제 응답의 청계2가 교차로 좌표는 청계천 부근(37.568N, 126.988E)에 떨어진다")
    void realSampleLandsOnCheonggyecheon() {
        double[] p = KoreaTm.toLatLng(198912.138883, 452082.387013);
        // 청계2가 교차로. 1e-3도는 약 100m.
        assertThat(p[0]).isCloseTo(37.568, within(2e-3));
        assertThat(p[1]).isCloseTo(126.988, within(2e-3));
    }
}
