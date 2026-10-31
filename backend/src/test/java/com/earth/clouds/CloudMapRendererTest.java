package com.earth.clouds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메르카토르 → 등장방형 재투영과 구름 판정을 검증한다. 원본과 같은 모양의 가짜 격자를 만들어,
 * 특정 위도·경도 띠에만 차가운(밝은) 값을 넣고 출력의 같은 위도·경도에 구름이 생기는지 본다.
 */
class CloudMapRendererTest {

    private static final int ROWS = 600;
    private static final int COLS = 1000;
    private static final double NORTH = 72.7;
    private static final double SOUTH = -72.7;
    private static final int WIDTH = 360;
    private static final int HEIGHT = WIDTH / 2;
    private static final float CLOUD = 250f;
    private static final float GROUND = 80f;

    @Test
    @DisplayName("메르카토르 격자의 위도 띠가 등장방형 이미지의 같은 위도로 옮겨진다")
    void latitudeBandLandsOnSameLatitude() {
        // 북위 50~52°만 구름. 메르카토르에서는 이 띠가 적도 쪽 띠보다 행을 더 많이 차지한다.
        byte[] out = CloudMapRenderer.render(source((lat, lon) -> lat >= 50 && lat <= 52), WIDTH);

        assertThat(pixel(out, 51.0, 0.0)).isGreaterThan(200);
        assertThat(pixel(out, 48.0, 0.0)).isZero();
        assertThat(pixel(out, 54.0, 0.0)).isZero();
        // 등장방형으로 옮기지 않고 행을 그대로 늘리면 띠가 엉뚱한 위도(약 36°)에 찍힌다.
        assertThat(pixel(out, 36.0, 0.0)).isZero();
    }

    @Test
    @DisplayName("원본 0열은 경도 -180°이고, 경도 180°를 넘어도 끊기지 않는다")
    void longitudeStartsAtMinus180AndWraps() {
        byte[] out = CloudMapRenderer.render(source((lat, lon) -> lon >= 120 && lon <= 125), WIDTH);

        assertThat(pixel(out, 0.0, 122.5)).isGreaterThan(200);
        assertThat(pixel(out, 0.0, -122.5)).isZero();
        assertThat(pixel(out, 0.0, 0.0)).isZero();
    }

    @Test
    @DisplayName("원본이 덮지 않는 극지방은 구름 없음으로 둔다")
    void polesOutsideSourceAreEmpty() {
        byte[] out = CloudMapRenderer.render(source((lat, lon) -> true), WIDTH);

        assertThat(pixel(out, 80.0, 0.0)).isZero();
        assertThat(pixel(out, -80.0, 0.0)).isZero();
        assertThat(pixel(out, 60.0, 0.0)).isGreaterThan(200);
    }

    @Test
    @DisplayName("고위도는 지표 자체가 차가워 구름 문턱을 올린다")
    void thresholdRisesWithLatitude() {
        assertThat(CloudMapRenderer.cloudThreshold(0)).isEqualTo(112.0);
        assertThat(CloudMapRenderer.cloudThreshold(70)).isEqualTo(150.0);
        assertThat(CloudMapRenderer.cloudThreshold(-35)).isBetween(112.0, 150.0);

        // 적도에서는 구름으로 보이는 밝기(130)가 고위도에서는 지표로 판정된다.
        byte[] out = CloudMapRenderer.render(source((lat, lon) -> true, 130f), WIDTH);
        assertThat(pixel(out, 0.0, 0.0)).isGreaterThan(0);
        assertThat(pixel(out, 68.0, 0.0)).isZero();
    }

    @Test
    @DisplayName("원본을 묶음 단위로 위에서 아래로 한 번씩만 읽는다")
    void readsSourceInOrderOnce() {
        List<Integer> starts = new ArrayList<>();
        CloudMapRenderer.Source base = source((lat, lon) -> false);
        CloudMapRenderer.render(new CloudMapRenderer.Source(ROWS, COLS, NORTH, SOUTH, (start, count) -> {
            starts.add(start);
            return base.reader().read(start, count);
        }), WIDTH);

        assertThat(starts).isSorted().doesNotHaveDuplicates();
        assertThat(starts.size()).isLessThanOrEqualTo(ROWS / 256 + 2);
    }

    @Test
    @DisplayName("JPEG로 인코딩된다")
    void encodesJpeg() {
        byte[] jpeg = CloudMapRenderer.encodeJpeg(CloudMapRenderer.render(source((lat, lon) -> lat > 0), WIDTH), WIDTH, HEIGHT);

        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    @DisplayName("파일 키에서 관측 시각을 읽는다")
    void parsesObservedAtFromKey() {
        String key = "GMGSI_LW/2026/10/28/09/GLOBCOMPLIR_v3r0_blend_s202610280900000_e202610280909599_c202610280933491.nc";

        assertThat(GmgsiFiles.observedAt(key)).hasValueSatisfying(
                t -> assertThat(t.toString()).isEqualTo("2026-10-28T09:00:00Z"));
    }

    private interface Cloudy {
        boolean at(double lat, double lon);
    }

    private static CloudMapRenderer.Source source(Cloudy cloudy) {
        return source(cloudy, CLOUD);
    }

    /** GMGSI와 같은 모양: 행은 메르카토르 등간격, 0열이 경도 -180°. */
    private static CloudMapRenderer.Source source(Cloudy cloudy, float cloudValue) {
        double mn = merc(NORTH);
        double ms = merc(SOUTH);
        return new CloudMapRenderer.Source(ROWS, COLS, NORTH, SOUTH, (start, count) -> {
            float[][] rows = new float[count][COLS];
            for (int i = 0; i < count; i++) {
                double y = mn - (mn - ms) * (start + i) / (ROWS - 1);
                double lat = Math.toDegrees(2 * Math.atan(Math.exp(y)) - Math.PI / 2);
                for (int c = 0; c < COLS; c++) {
                    double lon = -180.0 + c * 360.0 / COLS;
                    rows[i][c] = cloudy.at(lat, lon) ? cloudValue : GROUND;
                }
            }
            return rows;
        });
    }

    private static int pixel(byte[] out, double lat, double lon) {
        int r = (int) Math.floor((90.0 - lat) / 180.0 * HEIGHT);
        int c = (int) Math.floor((lon + 180.0) / 360.0 * WIDTH);
        return out[r * WIDTH + c] & 0xFF;
    }

    private static double merc(double lat) {
        return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }
}
