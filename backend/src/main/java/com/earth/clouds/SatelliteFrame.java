package com.earth.clouds;

import java.time.Instant;

/**
 * 위성 한 기의 영상 한 장을 지구본과 같은 등장방형 격자(가로 width, 세로 width/2)로 옮긴 것.
 *
 * <p>값은 {@code short}로 들고 있다(위성 다섯 장을 계속 메모리에 두므로 float의 절반). GIBS 영상은
 * 섭씨 × 10, EUMETView 영상은 회색 밝기 0~255다. 값이 없는 칸(원판 밖)은 {@link #NO_DATA}.
 */
public record SatelliteFrame(GeoSatellite satellite, Instant observedAt, int width, short[] values) {

    public static final short NO_DATA = Short.MIN_VALUE;

    public int height() {
        return width / 2;
    }
}
