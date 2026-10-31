package com.earth.clouds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 위성 영상 합성: 시야 각도, 가중치, EUMETView 환산, GMGSI 채움, 구름 판정. */
class CloudComposerTest {

    private static final int WIDTH = 360;
    private static final int HEIGHT = WIDTH / 2;
    private static final Instant T = Instant.parse("2026-10-31T04:30:00Z");

    private static int index(double lat, double lon) {
        int r = (int) Math.floor((90 - lat) / 180.0 * HEIGHT);
        int c = (int) Math.floor((lon + 180) / 360.0 * WIDTH);
        return Math.min(r, HEIGHT - 1) * WIDTH + Math.min(c, WIDTH - 1);
    }

    private static SatelliteFrame filled(GeoSatellite satellite, short value) {
        short[] v = new short[WIDTH * HEIGHT];
        Arrays.fill(v, value);
        return new SatelliteFrame(satellite, T, WIDTH, v);
    }

    @Test
    @DisplayName("직하점은 천정각 0°, 원판 가장자리(약 81.3° 떨어진 지점)는 거의 90°")
    void zenithGeometry() {
        assertThat(CloudComposer.cosZenith(1.0)).isCloseTo(1.0, within(1e-9));
        assertThat(CloudComposer.cosZenith(Math.cos(Math.toRadians(81.3)))).isCloseTo(0.0, within(0.01));
        assertThat(CloudComposer.cosZenith(Math.cos(Math.toRadians(45)))).isBetween(0.5, 0.7);
    }

    @Test
    @DisplayName("한 지점을 두 위성이 보면 더 정면에서 보는 위성의 값을 따른다")
    void prefersSatelliteLookingStraightDown() {
        // Himawari(140.7°E)는 맑음(30°C), GOES-West(137.2°W)는 짙은 구름(-60°C)
        List<SatelliteFrame> frames = List.of(
                filled(GeoSatellite.HIMAWARI, (short) 300), filled(GeoSatellite.GOES_WEST, (short) -600));

        byte[] out = CloudComposer.compose(WIDTH, frames, null).gray();

        assertThat(out[index(0, 140)] & 0xFF).isZero();
        assertThat(out[index(0, -137)] & 0xFF).isEqualTo(255);
        // 두 위성 사이(180° 부근)는 둘이 섞여 중간값
        assertThat(out[index(0, 179)] & 0xFF).isBetween(1, 254);
    }

    @Test
    @DisplayName("EUMETView 밝기를 겹치는 GIBS 영상과 맞춘 직선으로 섭씨로 바꾼다")
    void calibratesEumetviewAgainstGibs() {
        // GOES-East와 MTG가 겹치는 대서양에 온도 = -0.3 × 밝기 + 20 관계를 심는다.
        // 겹침 표본이 5,000칸은 넘어야 맞춘 값을 믿으므로 실제와 같은 크기로 만든다.
        int width = 2048;
        short[] gibs = new short[width * width / 2];
        short[] gray = new short[width * width / 2];
        for (int i = 0; i < gibs.length; i++) {
            int g = 10 + (i * 7919) % 240;
            gray[i] = (short) g;
            gibs[i] = (short) Math.round((-0.3 * g + 20) * 10);
        }
        List<SatelliteFrame> frames = List.of(
                new SatelliteFrame(GeoSatellite.GOES_EAST, T, width, gibs),
                new SatelliteFrame(GeoSatellite.MTG, T, width, gray));

        CloudComposer.Calibration c = CloudComposer.compose(width, frames, null).calibrations().get(GeoSatellite.MTG);

        assertThat(c.fitted()).isTrue();
        assertThat(c.slope()).isCloseTo(-0.3, within(0.001));
        assertThat(c.intercept()).isCloseTo(20.0, within(0.1));
    }

    @Test
    @DisplayName("겹치는 GIBS 영상이 없으면 미리 맞춰 둔 기본값으로 환산한다")
    void fallsBackToDefaultCalibration() {
        CloudComposer.Calibration c = CloudComposer.compose(WIDTH,
                List.of(filled(GeoSatellite.IODC, (short) 100)), null).calibrations().get(GeoSatellite.IODC);

        assertThat(c.fitted()).isFalse();
        assertThat(c.slope()).isNegative();
    }

    @Test
    @DisplayName("어느 위성도 보지 못한 칸만 GMGSI로 채우고, 그 비율을 알려 준다")
    void fillsGapsWithGmgsi() {
        byte[] gmgsi = new byte[WIDTH * HEIGHT];
        Arrays.fill(gmgsi, (byte) 77);

        CloudComposer.Result result = CloudComposer.compose(WIDTH,
                List.of(filled(GeoSatellite.HIMAWARI, (short) 300)), gmgsi);

        assertThat(result.gray()[index(0, 140)] & 0xFF).isZero();      // 위성이 본 곳(맑음)
        assertThat(result.gray()[index(0, -40)] & 0xFF).isEqualTo(77); // 반대편은 GMGSI
        assertThat(result.fallbackShare()).isBetween(0.3, 0.8);
    }

    @Test
    @DisplayName("맑은 하늘 기준보다 차가울수록 짙고, 고위도는 기준을 낮춘다")
    void densityFollowsTemperature() {
        assertThat(CloudComposer.density(20, CloudComposer.clearSkyCelsius(0))).isZero();
        assertThat(CloudComposer.density(-40, CloudComposer.clearSkyCelsius(0))).isEqualTo(255);
        assertThat(CloudComposer.density(-10, CloudComposer.clearSkyCelsius(0))).isBetween(100, 220);
        // 같은 -10°C도 위도 70°에서는 지표일 수 있어 구름으로 보지 않는다.
        assertThat(CloudComposer.density(-10, CloudComposer.clearSkyCelsius(70))).isZero();
    }
}
