package com.earth.domain.event;

/**
 * 지구본 화면에 보이는 범위. 경도는 날짜변경선을 넘으면 두 구간으로 나뉜다.
 *
 * <p>클라이언트가 보낸 값을 그대로 {@code longitude between west and east}에 넣으면 두 가지가 깨진다.
 * <ul>
 *   <li>MapLibre globe의 {@code getBounds()}는 경도를 감지 않고(unwrapped) 돌려준다. 날짜변경선을 넘는
 *       화면은 {@code west=170, east=190}처럼 오는데, 저장된 경도는 -180~180이라 동쪽 절반이 빠진다.</li>
 *   <li>이미 감긴 값이 오면 {@code west=170, east=-170}이 되고, {@code between 170 and -170}은 빈 결과다.</li>
 * </ul>
 * 그래서 경도 폭을 먼저 구하고, 서쪽 끝을 -180~180으로 감은 뒤, 180을 넘으면 두 구간으로 자른다.
 * 넘지 않으면 두 구간이 같아서 쿼리는 하나로 유지된다.
 */
public record BoundingBox(double southLat, double northLat,
                          double westLng1, double eastLng1,
                          double westLng2, double eastLng2) {

    public static BoundingBox of(double southLat, double northLat, double westLng, double eastLng) {
        if (!Double.isFinite(southLat) || !Double.isFinite(northLat)
                || !Double.isFinite(westLng) || !Double.isFinite(eastLng)) {
            throw new IllegalArgumentException("범위 좌표가 유효한 숫자가 아닙니다.");
        }
        // 극지방이 화면에 들어오면 위도가 ±90을 조금 넘어 올 수 있다. 오류가 아니라 극까지 보인다는 뜻이다.
        double south = Math.max(-90, southLat);
        double north = Math.min(90, northLat);
        if (south > north) {
            throw new IllegalArgumentException("남쪽 위도가 북쪽 위도보다 큽니다.");
        }

        double span = eastLng - westLng;
        if (span < 0) span += 360;   // 이미 감겨서 온 값(west=170, east=-170)
        if (span >= 360) {
            return new BoundingBox(south, north, -180, 180, -180, 180);
        }

        double west = wrap(westLng);
        double east = west + span;
        if (east <= 180) {
            return new BoundingBox(south, north, west, east, west, east);
        }
        return new BoundingBox(south, north, west, 180, -180, east - 360);
    }

    /** -180 이상 180 미만으로 감는다. */
    private static double wrap(double lng) {
        return ((lng + 180) % 360 + 360) % 360 - 180;
    }
}
