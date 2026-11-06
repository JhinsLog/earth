package com.earth.clouds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoRingClientTest {

    @Test
    @DisplayName("EUMETView 조각은 직하점 ±80°를 빈틈·겹침 없이 2×2로 덮고, 지구본 픽셀 경계에 맞는다")
    void tilesCoverSatelliteViewWithoutGaps() {
        int width = 4096;
        List<GeoRingClient.Tile> tiles = GeoRingClient.tiles(45.5, width);

        assertThat(tiles).hasSize(4);
        boolean[] covered = new boolean[width * width / 2];
        for (GeoRingClient.Tile t : tiles) {
            for (int r = t.r0(); r < t.r1(); r++) {
                for (int c = t.c0(); c < t.c1(); c++) {
                    assertThat(covered[r * width + c]).as("겹침 %d,%d", r, c).isFalse();
                    covered[r * width + c] = true;
                }
            }
        }
        // 직하점 경도와 ±79°는 덮이고, ±81° 밖은 받지 않는다(적도 줄 기준).
        int equator = width / 4;
        double degPerPx = 360.0 / width;
        assertThat(covered[equator * width + (int) ((45.5 + 180) / degPerPx)]).isTrue();
        assertThat(covered[equator * width + (int) ((45.5 - 79 + 180) / degPerPx)]).isTrue();
        assertThat(covered[equator * width + (int) ((45.5 + 79 + 180) / degPerPx)]).isTrue();
        assertThat(covered[equator * width + (int) ((45.5 + 81 + 180) / degPerPx)]).isFalse();
        // 조각 하나가 약 910px — 1픽셀이 지구본과 같은 0.088°라 EUMETView가 줄여 둔 자료를 쓰지 않는다.
        assertThat(tiles.getFirst().c1() - tiles.getFirst().c0()).isBetween(900, 920);
    }

    @Test
    @DisplayName("조각의 BBOX는 WMS 1.3.0 EPSG:4326 순서(위도, 경도)로 픽셀 경계를 그대로 쓴다")
    void tileBboxMatchesPixelEdges() {
        GeoRingClient.Tile tile = new GeoRingClient.Tile(0, 2048, 0, 1024);
        assertThat(tile.bbox(4096)).isEqualTo("0.0,-180.0,90.0,0.0");
    }
}
