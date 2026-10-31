package com.earth.clouds;

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
 * 45°C 더 차가우면 가장 짙다. 고위도는 지표 자체가 차가워 기준을 낮춘다.
 *
 * <p>어느 위성도 보지 못한 칸(위성 하나가 빠졌을 때)은 GMGSI 이미지로 채운다.
 */
public final class CloudComposer {

    /** 정지궤도 반지름 ÷ 지구 반지름(42,164km ÷ 6,378km). */
    static final double ORBIT_RADIUS = 6.6107;
    static final double MIN_COS_ZENITH = 0.15;

    /** 온도 = slope × 밝기 + intercept. */
    public record Calibration(double slope, double intercept, int samples, double rmsCelsius, boolean fitted) {
    }

    /**
     * @param gray      등장방형 흑백 픽셀(행 우선). 밝을수록 구름이 짙다
     * @param satelliteCoverage 위성 영상으로 채운 칸의 비율(0~1). 극지방은 어느 위성도 못 봐 1이 되지 않는다
     * @param fallbackShare 지구본이 실시간 구름을 쓰는 위도 ±60° 안에서 GMGSI로 채운 칸의 비율
     */
    public record Result(byte[] gray, double satelliteCoverage, double fallbackShare,
                         Map<GeoSatellite, Calibration> calibrations) {
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

    /** @param fallback GMGSI 흑백 픽셀(같은 크기). 없으면 null */
    public static Result compose(int width, List<SatelliteFrame> frames, byte[] fallback) {
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
        long covered = 0;
        long band = 0;
        long filled = 0;
        for (int r = 0; r < height; r++) {
            double clearSky = clearSkyCelsius(latDeg[r]);
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
                    out[i] = (byte) density(celsiusSum / weightSum, clearSky);
                } else if (fallback != null) {
                    out[i] = fallback[i];
                    if (inBand) {
                        filled++;
                    }
                }
            }
        }
        return new Result(out, (double) covered / out.length, (double) filled / band, calibrations);
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

    /** 0~255. 감마 0.8로 옅은 구름을 조금 살린다(GMGSI 변환과 같은 곡선). */
    static int density(double celsius, double clearSky) {
        double t = Math.clamp((clearSky - celsius) / 45.0, 0.0, 1.0);
        return (int) Math.round(Math.pow(t, 0.8) * 255);
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
