package com.earth.clouds;

import com.earth.config.CloudProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 위성마다 가장 최근 영상 한 장을 들고 있다가, 새 영상이 올라왔으면 바꿔 끼운다.
 *
 * <p><b>최신 영상 찾기.</b> 올라오는 데 20~50분 걸리고 일정하지 않다.
 * <ul>
 *   <li>GIBS: 레이어별 시각 목록(WMTS DescribeDomains, 300바이트 남짓)에서 최신 시각을 고른다. 같은 요청에도
 *       빈 영상과 정상 영상이 번갈아 오는 일이 있어(2026-10-31 같은 주소를 세 번 물어 1~2번만 정상),
 *       빈 영상이면 몇 번 다시 묻는다.</li>
 *   <li>EUMETView: 목록을 묻는 가벼운 방법이 없어, 관측 간격의 시각을 지금부터 거슬러 올라가며 64×32
 *       크기로 있는지만 묻고 있을 때 전체 크기로 받는다. 이 서버는 응답이 일정하다.</li>
 * </ul>
 * 어느 쪽이든 이미 가진 시각보다 새것이 없으면 받지 않는다.
 *
 * <p>위성 하나가 실패해도 나머지는 계속 간다. 오래된 영상(3시간 초과)은 버리고, 빈 지역은
 * {@link CloudComposer}가 GMGSI로 채운다.
 */
@Component
public class GeoRingFrames {

    /** 위성 영상 한 장(ARGB 픽셀, 행 우선)과 실제 관측 시각. */
    public record Fetched(int[] argb, Instant observedAt) {
    }

    /** 실제 HTTP는 {@link GeoRingClient}. 테스트에서는 가짜로 바꾼다. */
    public interface Fetcher {
        /** 그 시각의 영상이 없으면 비어 있다. */
        Optional<Fetched> fetch(GeoSatellite satellite, Instant time, int width);

        /** GIBS 레이어의 관측 시각 목록(구간 안). */
        List<Instant> availableTimes(GeoSatellite satellite, Instant from, Instant to);

        String colormapXml();
    }

    static final int MAX_PROBES = 12;
    static final int GIBS_ATTEMPTS = 3;
    /** 그 시각에 영상이 있는지만 볼 때의 크기. 전체 크기로 물으면 새 영상이 없을 때도 한 번에 30초 넘게 걸렸다. */
    static final int PROBE_WIDTH = 64;
    static final Duration MAX_FRAME_AGE = Duration.ofHours(3);
    private static final Duration COLORMAP_TTL = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(GeoRingFrames.class);

    private final Fetcher fetcher;
    private final int width;
    private final Map<GeoSatellite, SatelliteFrame> frames = new EnumMap<>(GeoSatellite.class);
    private IrColormap colormap;
    private Instant colormapLoadedAt;

    public GeoRingFrames(Fetcher fetcher, CloudProperties properties) {
        this.fetcher = fetcher;
        this.width = properties.width();
    }

    /** 위성마다 새 영상이 있는지 보고 받는다. 하나라도 바뀌면 true. 스케줄러 스레드 하나에서만 부른다. */
    public synchronized boolean refresh(Instant now) {
        boolean changed = false;
        for (GeoSatellite satellite : GeoSatellite.values()) {
            try {
                Optional<SatelliteFrame> fresh = newer(satellite, now);
                if (fresh.isPresent()) {
                    frames.put(satellite, fresh.get());
                    changed = true;
                }
            } catch (RuntimeException e) {
                log.warn("{} 영상을 받지 못했습니다. 이전 영상을 씁니다: {}", satellite.label(), e.toString());
            }
        }
        // 오래된 영상이 빠지는 것도 결과가 바뀌는 일이다.
        changed |= frames.values().removeIf(frame -> frame.observedAt().isBefore(now.minus(MAX_FRAME_AGE)));
        return changed;
    }

    public synchronized List<SatelliteFrame> current() {
        return new ArrayList<>(frames.values());
    }

    private Optional<SatelliteFrame> newer(GeoSatellite satellite, Instant now) {
        if (satellite.provider() == GeoSatellite.Provider.GIBS && !colormapReady(now)) {
            return Optional.empty();
        }
        Instant known = Optional.ofNullable(frames.get(satellite)).map(SatelliteFrame::observedAt).orElse(null);
        if (satellite.provider() == GeoSatellite.Provider.GIBS) {
            return newerFromGibs(satellite, now, known);
        }
        long cadence = satellite.cadenceMinutes() * 60L;
        Instant newestSlot = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), cadence) * cadence);
        for (int i = 0; i < MAX_PROBES; i++) {
            Instant slot = newestSlot.minusSeconds(cadence * i);
            if (known != null && !slot.isAfter(known)) {
                return Optional.empty();
            }
            Optional<Fetched> probe = fetcher.fetch(satellite, slot, PROBE_WIDTH);
            if (probe.isEmpty() || !hasPixels(probe.get().argb())) {
                continue;
            }
            Instant observedAt = probe.get().observedAt();
            if (known != null && !observedAt.isAfter(known)) {
                return Optional.empty();
            }
            Optional<Fetched> fetched = fetcher.fetch(satellite, observedAt, width);
            if (fetched.isEmpty()) {
                continue;
            }
            short[] values = decode(satellite, fetched.get().argb());
            if (values != null) {
                return Optional.of(new SatelliteFrame(satellite, observedAt, width, values));
            }
        }
        return Optional.empty();
    }

    private Optional<SatelliteFrame> newerFromGibs(GeoSatellite satellite, Instant now, Instant known) {
        List<Instant> times = fetcher.availableTimes(satellite, now.minus(MAX_FRAME_AGE), now).stream()
                .filter(t -> known == null || t.isAfter(known))
                .sorted(java.util.Comparator.reverseOrder())
                .limit(MAX_PROBES)
                .toList();
        for (Instant time : times) {
            for (int attempt = 0; attempt < GIBS_ATTEMPTS; attempt++) {
                Optional<Fetched> fetched = fetcher.fetch(satellite, time, width);
                short[] values = fetched.map(f -> decode(satellite, f.argb())).orElse(null);
                if (values != null) {
                    return Optional.of(new SatelliteFrame(satellite, time, width, values));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasPixels(int[] argb) {
        for (int pixel : argb) {
            if (pixel >>> 24 >= 128) {
                return true;
            }
        }
        return false;
    }

    /** 유효한 픽셀이 하나도 없으면(빈 영상) null. */
    private short[] decode(GeoSatellite satellite, int[] argb) {
        short[] values = new short[argb.length];
        boolean any = false;
        for (int i = 0; i < argb.length; i++) {
            int alpha = argb[i] >>> 24;
            if (alpha < 128) {
                values[i] = SatelliteFrame.NO_DATA;
                continue;
            }
            any = true;
            values[i] = satellite.provider() == GeoSatellite.Provider.GIBS
                    ? (short) Math.round(colormap.celsius(argb[i]) * 10)
                    : (short) ((argb[i] >> 16) & 0xFF);
        }
        if (!any) {
            return null;
        }
        return satellite.provider() == GeoSatellite.Provider.GIBS ? median3x3(values, width, width / 2) : values;
    }

    /**
     * 3×3 이웃의 중앙값. 가장 가까운 색으로 되돌려도 섞인 색 일부는 엉뚱한 온도가 되어 점 잡음으로 남는다
     * (시제품에서 이 필터 하나로 EUMETView 환산 오차가 15°C → 6.5°C로 줄었다). 값이 없는 이웃은 뺀다.
     */
    static short[] median3x3(short[] values, int width, int height) {
        short[] out = new short[values.length];
        short[] window = new short[9];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (values[i] == SatelliteFrame.NO_DATA) {
                    out[i] = SatelliteFrame.NO_DATA;
                    continue;
                }
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        // 경도는 ±180°에서 이어진다.
                        int xx = Math.floorMod(x + dx, width);
                        short v = values[yy * width + xx];
                        if (v != SatelliteFrame.NO_DATA) {
                            window[n++] = v;
                        }
                    }
                }
                Arrays.sort(window, 0, n);
                out[i] = window[n / 2];
            }
        }
        return out;
    }

    private boolean colormapReady(Instant now) {
        if (colormap != null && colormapLoadedAt.plus(COLORMAP_TTL).isAfter(now)) {
            return true;
        }
        try {
            colormap = IrColormap.parse(fetcher.colormapXml());
            colormapLoadedAt = now;
        } catch (RuntimeException e) {
            // 새로 받지 못했어도 예전 표가 있으면 그대로 쓴다. GIBS가 표를 바꾸는 일은 드물다.
            log.warn("GIBS 색상표를 받지 못했습니다: {}", e.toString());
        }
        return colormap != null;
    }
}
