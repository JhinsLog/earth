package com.earth.clouds;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 위성 다섯 기의 영상을 지구본 구름 이미지 한 장으로 합친다.
 *
 * <p><b>합치기.</b> 같은 지점을 여러 위성이 보면, 더 정면에서 보는 위성에 더 큰 가중치를 준다. 위성은 원판
 * 가장자리로 갈수록 비스듬히 봐서 해상도가 떨어지고 구름이 실제보다 두꺼워 보인다. 가중치는
 * {@code max(cos 천정각 - 0.15, 0)^3}이라 천정각 약 81° 밖은 쓰지 않고, 경계에서는 두 위성이 부드럽게 섞인다.
 *
 * <p><b>EUMETView 환산.</b> 밝기→온도 공식이 공개돼 있지 않다. 그래서 GIBS 영상(섭씨로 되돌릴 수 있음)과
 * 겹치는 지역에서 매번 직선(온도 = a × 밝기 + b)을 맞춘다. 겹침이 적거나 결과가 이상하면 시제품에서 맞춘
 * 기본값을 쓴다.
 *
 * <p><b>구름 판정.</b> 밝기온도가 맑은 하늘 기준(적도 12°C, 위도 70° 이상 -18°C)보다 차가울수록 짙은 구름이다.
 * 고위도는 지표 자체가 차가워 기준을 낮춘다. 얼마나 더 차가워야 가장 짙은지({@link #fullCloudDepth})는 위도마다
 * 다르다: 지표에서 대류권 꼭대기까지 온도 폭의 75%(최소 45°C). 처음엔 모든 위도에 45°C를 썼는데, 열대는 구름
 * 꼭대기가 -60~-80°C까지 올라가 거의 모든 소나기구름이 단계 없이 새하얀 덩어리가 됐다(열대 구름 픽셀의 17~25%가
 * 화면에서 완전 불투명, 중위도는 2~5%. 2026-11-06 측정).
 *
 * <p><b>바다의 맑은 날 온도.</b> 바다 칸은 위도 기준 대신 그 칸의 최근 최고 온도에서 {@link #SEA_MARGIN}을 뺀 값을
 * 기준으로 쓴다(둘 중 높은 쪽). 이유는 {@link ClearSkyBackground}.
 *
 * <p><b>진하기 곡선.</b> 기준보다 {@link #KNEE}°C 차가우면 0.5, 거기서 "가장 짙은 온도"(위도 기준 - 폭)까지 1로
 * 곧게 오른다. 예전 곡선(폭 전체에 감마 0.8)에서는 바다보다 15°C 차가운 낮은 구름이 0.3 남짓이라 화면에서 거의
 * 보이지 않았다. 가시광 사진에서는 하얗게 보이는 구름이다. 가장 짙은 온도는 위도 기준에 묶어 두어, 바다 기준이
 * 올라가도 열대 소나기구름이 다시 일찍 새하얘지지 않는다.
 *
 * <p>어느 위성도 보지 못한 칸(위성 하나가 빠졌을 때)은 GMGSI 이미지로 채운다.
 */
public final class CloudComposer {

    /** 정지궤도 반지름 ÷ 지구 반지름(42,164km ÷ 6,378km). */
    static final double ORBIT_RADIUS = 6.6107;
    static final double MIN_COS_ZENITH = 0.15;
    /** 바다 기준을 최근 최고 온도보다 이만큼 낮춘다. 맑은 바다의 밤 냉각·측정 잡음이 구름으로 잡히지 않게. */
    static final double SEA_MARGIN = 3.0;
    static final double KNEE = 15.0;

    /** 온도 = slope × 밝기 + intercept. */
    public record Calibration(double slope, double intercept, int samples, double rmsCelsius, boolean fitted) {
    }

    /**
     * @param gray      등장방형 흑백 픽셀(행 우선). 밝을수록 구름이 짙다
     * @param satelliteCoverage 위성 영상으로 채운 칸의 비율(0~1). 극지방은 어느 위성도 못 봐 1이 되지 않는다
     * @param fallbackShare 지구본이 실시간 구름을 쓰는 위도 ±60° 안에서 GMGSI로 채운 칸의 비율
     * @param celsius   위성들을 합친 밝기온도(섭씨 × 10). 위성이 못 본 칸은 {@link SatelliteFrame#NO_DATA}
     */
    public record Result(byte[] gray, double satelliteCoverage, double fallbackShare,
                         Map<GeoSatellite, Calibration> calibrations, short[] celsius) {
    }

    // 2026-10-31 시제품에서 겹침 지역으로 맞춘 값(겹침 12만·5만 픽셀, RMS 6.5·6.9°C).
    private static final Map<GeoSatellite, Calibration> DEFAULTS = Map.of(
            GeoSatellite.MTG, new Calibration(-0.368, 27.7, 0, Double.NaN, false),
            GeoSatellite.IODC, new Calibration(-0.249, 22.8, 0, Double.NaN, false));
    private static final int MIN_FIT_SAMPLES = 5_000;
    private static final double MAX_FIT_RMS = 12.0;
    private static final int FIT_STRIDE = 2;
    private static final double FIT_MIN_COS_ZENITH = 0.35;

    private CloudComposer() {
    }

    public static Result compose(int width, List<SatelliteFrame> frames, byte[] fallback) {
        return compose(width, frames, fallback, null);
    }

    /**
     * @param fallback   GMGSI 흑백 픽셀(같은 크기). 없으면 null
     * @param background 바다의 맑은 날 온도. 없으면 null(모든 칸에 위도 기준)
     */
    public static Result compose(int width, List<SatelliteFrame> frames, byte[] fallback,
                                 ClearSkyBackground background) {
        int height = width / 2;
        double[] cosLat = new double[height];
        double[] latDeg = new double[height];
        for (int r = 0; r < height; r++) {
            latDeg[r] = 90.0 - (r + 0.5) * 180.0 / height;
            cosLat[r] = Math.cos(Math.toRadians(latDeg[r]));
        }
        Map<GeoSatellite, double[]> cosDLon = new EnumMap<>(GeoSatellite.class);
        for (SatelliteFrame frame : frames) {
            double[] c = new double[width];
            for (int x = 0; x < width; x++) {
                double lon = -180.0 + (x + 0.5) * 360.0 / width;
                c[x] = Math.cos(Math.toRadians(lon - frame.satellite().subLongitude()));
            }
            cosDLon.put(frame.satellite(), c);
        }

        Map<GeoSatellite, Calibration> calibrations = new EnumMap<>(GeoSatellite.class);
        for (SatelliteFrame frame : frames) {
            if (frame.satellite().provider() == GeoSatellite.Provider.EUMETVIEW) {
                calibrations.put(frame.satellite(), calibrate(frame, frames, width, cosLat, cosDLon));
            }
        }

        byte[] out = new byte[width * height];
        short[] temps = new short[width * height];
        Arrays.fill(temps, SatelliteFrame.NO_DATA);
        long covered = 0;
        long band = 0;
        long filled = 0;
        for (int r = 0; r < height; r++) {
            double clearSky = clearSkyCelsius(latDeg[r]);
            double fullCold = clearSky - fullCloudDepth(latDeg[r]);
            boolean inBand = Math.abs(latDeg[r]) <= 60.0;
            for (int x = 0; x < width; x++) {
                int i = r * width + x;
                double weightSum = 0;
                double celsiusSum = 0;
                for (SatelliteFrame frame : frames) {
                    short v = frame.values()[i];
                    if (v == SatelliteFrame.NO_DATA) {
                        continue;
                    }
                    double cz = cosZenith(cosLat[r] * cosDLon.get(frame.satellite())[x]);
                    if (cz <= MIN_COS_ZENITH) {
                        continue;
                    }
                    double w = Math.pow(cz - MIN_COS_ZENITH, 3);
                    weightSum += w;
                    celsiusSum += w * celsius(frame, v, calibrations);
                }
                if (inBand) {
                    band++;
                }
                if (weightSum > 0) {
                    covered++;
                    double celsius = celsiusSum / weightSum;
                    temps[i] = (short) Math.round(celsius * 10);
                    double threshold = clearSky;
                    if (background != null && background.at(i) != SatelliteFrame.NO_DATA) {
                        threshold = Math.max(clearSky, background.at(i) / 10.0 - SEA_MARGIN);
                    }
                    out[i] = (byte) density(celsius, threshold, fullCold);
                } else if (fallback != null) {
                    out[i] = fallback[i];
                    if (inBand) {
                        filled++;
                    }
                }
            }
        }
        return new Result(out, (double) covered / out.length, (double) filled / band, calibrations, temps);
    }

    /**
     * 위성에서 본 천정각의 코사인. 지구 중심에서 지점까지(단위 벡터 p)와 위성 위치(직하점 방향으로 R) 사이에서
     * {@code cos = p·(s - p) / |s - p| = (R·x - 1) / sqrt(R² - 2R·x + 1)}, x는 cos(위도)·cos(경도차).
     */
    static double cosZenith(double x) {
        return (ORBIT_RADIUS * x - 1) / Math.sqrt(ORBIT_RADIUS * ORBIT_RADIUS - 2 * ORBIT_RADIUS * x + 1);
    }

    static double clearSkyCelsius(double latDeg) {
        double k = Math.min(Math.abs(latDeg) / 70.0, 1.0);
        return 12.0 - 30.0 * k * k;
    }

    /**
     * 맑은 하늘 기준보다 이만큼(°C) 차가우면 가장 짙은 구름으로 본다. 대류권 꼭대기(구름이 더 올라가지 못하는 높이)의
     * 온도는 적도 약 -75°C, 극 쪽 약 -55°C라 지표와의 폭이 열대에서 훨씬 넓다. 같은 45°C를 쓰면 열대 구름은 중간
     * 높이만 돼도 최대가 된다. 폭의 75%를 쓰고, 중위도·고위도는 원래 값(45°C)보다 진해지지 않게 아래를 막는다.
     */
    static double fullCloudDepth(double latDeg) {
        double k = Math.min(Math.abs(latDeg) / 70.0, 1.0);
        double tropopause = -75.0 + 20.0 * k * k;
        return Math.max(45.0, 0.75 * (clearSkyCelsius(latDeg) - tropopause));
    }

    /**
     * 0~255. 기준보다 {@link #KNEE}°C 차가우면 절반, {@code fullCold}°C 이하면 최대.
     *
     * @param threshold 이보다 따뜻하면 맑음
     * @param fullCold  이보다 차가우면 가장 짙은 구름(위도 기준 - {@link #fullCloudDepth})
     */
    static int density(double celsius, double threshold, double fullCold) {
        double below = threshold - celsius;
        if (below <= 0) {
            return 0;
        }
        double t = below < KNEE
                ? 0.5 * below / KNEE
                : 0.5 + 0.5 * (threshold - KNEE - celsius) / Math.max(threshold - KNEE - fullCold, 1.0);
        return (int) Math.round(Math.clamp(t, 0.0, 1.0) * 255);
    }

    private static double celsius(SatelliteFrame frame, short v, Map<GeoSatellite, Calibration> calibrations) {
        if (frame.satellite().provider() == GeoSatellite.Provider.GIBS) {
            return v / 10.0;
        }
        Calibration c = calibrations.get(frame.satellite());
        return c.slope() * v + c.intercept();
    }

    /** GIBS 영상들과 둘 다 비교적 정면에서 보는 칸만 골라 최소제곱 직선을 맞춘다. */
    static Calibration calibrate(SatelliteFrame target, List<SatelliteFrame> frames, int width,
                                 double[] cosLat, Map<GeoSatellite, double[]> cosDLon) {
        int height = width / 2;
        double n = 0, sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        double[] targetCos = cosDLon.get(target.satellite());
        for (SatelliteFrame reference : frames) {
            if (reference.satellite().provider() != GeoSatellite.Provider.GIBS) {
                continue;
            }
            double[] refCos = cosDLon.get(reference.satellite());
            for (int r = 0; r < height; r += FIT_STRIDE) {
                for (int x = 0; x < width; x += FIT_STRIDE) {
                    int i = r * width + x;
                    short g = target.values()[i];
                    short t = reference.values()[i];
                    // 밝기 양 끝(0·255 부근)은 잘린 값이라 직선에서 뺀다.
                    if (g == SatelliteFrame.NO_DATA || t == SatelliteFrame.NO_DATA || g <= 2 || g >= 253) {
                        continue;
                    }
                    if (cosZenith(cosLat[r] * targetCos[x]) < FIT_MIN_COS_ZENITH
                            || cosZenith(cosLat[r] * refCos[x]) < FIT_MIN_COS_ZENITH) {
                        continue;
                    }
                    double c = t / 10.0;
                    n++;
                    sx += g;
                    sy += c;
                    sxx += (double) g * g;
                    sxy += g * c;
                    syy += c * c;
                }
            }
        }
        Calibration fallback = DEFAULTS.get(target.satellite());
        double denominator = n * sxx - sx * sx;
        if (n < MIN_FIT_SAMPLES || denominator == 0) {
            return fallback;
        }
        double slope = (n * sxy - sx * sy) / denominator;
        double intercept = (sy - slope * sx) / n;
        // 잔차 제곱합 = Σ(y - a·x - b)², 전개해서 한 번에 구한다.
        double sse = syy - 2 * slope * sxy - 2 * intercept * sy + slope * slope * sxx
                + 2 * slope * intercept * sx + n * intercept * intercept;
        double rms = Math.sqrt(Math.max(sse, 0) / n);
        // 차가울수록 밝으니 기울기는 음수여야 한다. 시제품의 두 위성은 -0.37, -0.25였다.
        if (slope > -0.1 || slope < -0.6 || rms > MAX_FIT_RMS) {
            return fallback;
        }
        return new Calibration(slope, intercept, (int) n, rms, true);
    }
}
