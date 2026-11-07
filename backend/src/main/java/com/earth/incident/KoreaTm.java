package com.earth.incident;

/**
 * 한국 중부원점 TM 좌표(GRS80, EPSG:5181) ↔ 위경도.
 *
 * <p>서울시 돌발정보는 위치를 {@code grs80tm_x/y}로만 준다. 원점은 북위 38°·동경 127°이고, 원점에 x 200,000m,
 * y 500,000m를 더한 값이다. 실제 응답(청계2가 교차로 x 198,912 / y 452,082)을 이 원점으로 풀면 청계천 위에
 * 떨어져, y에 600,000을 더하는 EPSG:5186이 아니라 이쪽임을 확인했다(2026-11-07).
 *
 * <p>변환 라이브러리 대신 직접 구현한 이유: 필요한 것은 이 투영 하나의 역변환뿐이고, 횡메르카토르 공식은
 * 표준(Snyder, "Map Projections — A Working Manual" 식 8-12·8-18 등)이라 검증이 쉽다. 의존성 하나를 늘리지 않는다.
 */
final class KoreaTm {

    private static final double A = 6_378_137.0;
    private static final double F = 1 / 298.257222101;
    private static final double E2 = 2 * F - F * F;
    private static final double EP2 = E2 / (1 - E2);
    private static final double LAT0 = Math.toRadians(38.0);
    private static final double LON0 = Math.toRadians(127.0);
    private static final double FALSE_EASTING = 200_000.0;
    private static final double FALSE_NORTHING = 500_000.0;
    private static final double M0 = meridianArc(LAT0);

    private KoreaTm() {
    }

    /** @return {위도, 경도} 도 단위 */
    static double[] toLatLng(double x, double y) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        double mu = (M0 + (y - FALSE_NORTHING)) / (A * (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));
        double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));
        double phi1 = mu
                + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
                + (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

        double sin1 = Math.sin(phi1);
        double cos1 = Math.cos(phi1);
        double tan1 = Math.tan(phi1);
        double c1 = EP2 * cos1 * cos1;
        double t1 = tan1 * tan1;
        double n1 = A / Math.sqrt(1 - E2 * sin1 * sin1);
        double r1 = A * (1 - E2) / Math.pow(1 - E2 * sin1 * sin1, 1.5);
        double d = (x - FALSE_EASTING) / n1;

        double lat = phi1 - (n1 * tan1 / r1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lon = LON0 + (d
                - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cos1;
        return new double[]{Math.toDegrees(lat), Math.toDegrees(lon)};
    }

    /** 정변환. 역변환을 검증하는 데만 쓴다. @return {x, y} */
    static double[] fromLatLng(double latDeg, double lngDeg) {
        double phi = Math.toRadians(latDeg);
        double sin = Math.sin(phi);
        double cos = Math.cos(phi);
        double t = Math.tan(phi) * Math.tan(phi);
        double c = EP2 * cos * cos;
        double n = A / Math.sqrt(1 - E2 * sin * sin);
        double a = (Math.toRadians(lngDeg) - LON0) * cos;
        double x = FALSE_EASTING + n * (a + (1 - t + c) * Math.pow(a, 3) / 6
                + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120);
        double y = FALSE_NORTHING + (meridianArc(phi) - M0) + n * Math.tan(phi) * (a * a / 2
                + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720);
        return new double[]{x, y};
    }

    /** 적도에서 위도 phi까지의 자오선 호 길이(m). */
    private static double meridianArc(double phi) {
        double e4 = E2 * E2;
        double e6 = e4 * E2;
        return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
                - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi)
                - (35 * e6 / 3072) * Math.sin(6 * phi));
    }
}
