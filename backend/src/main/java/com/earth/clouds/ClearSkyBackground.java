package com.earth.clouds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * 바다 칸마다 최근 12~24시간 동안 가장 따뜻했던 밝기온도. 그 칸의 "맑은 날 온도"로 쓴다.
 *
 * <p><b>왜.</b> 위도마다 하나로 정한 맑은 하늘 기준(예: 위도 40° 약 2°C)은 겨울 시베리아처럼 차가운 땅이 구름으로
 * 잡히지 않게 낮춰 둔 값이다. 그런데 바다는 그보다 훨씬 따뜻해(11월 동해 약 15°C), 꼭대기가 -3°C쯤인 낮은
 * 구름(찬 공기가 바다를 건널 때 생기는 줄무늬 구름)이 기준과 5°C밖에 차이 나지 않아 거의 지워졌다
 * (2026-11-07 동해 측정: 구름 꼭대기 중앙값 -3°C, 합성 밀도 0.16). 칸마다 실제 맑은 날 온도를 알면 바다보다
 * 18°C 차가운 구름으로 제대로 본다.
 *
 * <p><b>왜 바다만.</b> 땅은 낮과 밤의 온도 차가 15~20°C라, 하루 최고 온도를 기준으로 삼으면 맑은 밤의 땅 전체가
 * 구름이 된다. 바다는 하루 변화가 1°C 안팎이다.
 *
 * <p><b>12~24시간.</b> 12시간짜리 묶음 두 개를 번갈아 쓴다. 칸마다 24시간 기록을 다 들고 있지 않아도 되고(지금
 * 묶음 + 직전 묶음, 4096 격자에서 32MB), 지나간 날씨는 늦어도 24시간 뒤엔 잊는다. 하루 넘게 계속 흐린 칸은 최고
 * 온도가 구름 온도라 효과가 없다. 그래도 합성은 위도 기준보다 낮아지지 않게 둘 중 큰 값을 쓰므로 예전보다 나빠지지는
 * 않는다. 메모리에만 두므로 재시작하면 처음부터 다시 쌓는다(그동안은 예전 방식과 같다).
 *
 * <p>육지·바다 구분은 NASA GIBS {@code OSM_Land_Water_Map}(© OpenStreetMap contributors, ODbL)을 4096×2048로
 * 받아 둔 {@code clouds/sea-mask.png}다. 프론트 라스의 바다 마스크와 같은 출처다.
 *
 * <p>스케줄러 한 실행만 만진다(같은 예약 작업은 겹쳐 돌지 않는다).
 */
public final class ClearSkyBackground {

    static final Duration BUCKET = Duration.ofHours(12);
    private static final String SEA_MASK = "/clouds/sea-mask.png";

    private final boolean[] sea;
    private short[] current;
    private short[] previous;
    private Instant bucketStart;

    ClearSkyBackground(boolean[] sea) {
        this.sea = sea;
        this.current = empty(sea.length);
        this.previous = empty(sea.length);
    }

    /** 바다 마스크를 {@code width × width/2} 격자에 맞춰 읽는다(가장 가까운 칸). */
    public static ClearSkyBackground load(int width) {
        try (InputStream in = ClearSkyBackground.class.getResourceAsStream(SEA_MASK)) {
            if (in == null) {
                throw new IllegalStateException(SEA_MASK + " 없음");
            }
            BufferedImage mask = ImageIO.read(in);
            int height = width / 2;
            boolean[] sea = new boolean[width * height];
            for (int r = 0; r < height; r++) {
                int y = (int) ((r + 0.5) * mask.getHeight() / height);
                for (int c = 0; c < width; c++) {
                    int x = (int) ((c + 0.5) * mask.getWidth() / width);
                    sea[r * width + c] = mask.getRaster().getSample(x, y, 0) > 127;
                }
            }
            return new ClearSkyBackground(sea);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @param celsius 합성된 밝기온도(섭씨 × 10, 행 우선). 값이 없는 칸은 {@link SatelliteFrame#NO_DATA} */
    public void record(short[] celsius, Instant now) {
        if (bucketStart == null || !now.isBefore(bucketStart.plus(BUCKET.multipliedBy(2)))) {
            // 처음이거나 24시간 넘게 끊겼으면 묵은 기록은 버린다.
            Arrays.fill(previous, SatelliteFrame.NO_DATA);
            Arrays.fill(current, SatelliteFrame.NO_DATA);
            bucketStart = now;
        } else if (!now.isBefore(bucketStart.plus(BUCKET))) {
            short[] recycled = previous;
            previous = current;
            current = recycled;
            Arrays.fill(current, SatelliteFrame.NO_DATA);
            bucketStart = now;
        }
        for (int i = 0; i < celsius.length; i++) {
            if (sea[i] && celsius[i] > current[i]) {
                current[i] = celsius[i];
            }
        }
    }

    /** 섭씨 × 10. 육지이거나 아직 기록이 없으면 {@link SatelliteFrame#NO_DATA}. */
    public short at(int index) {
        return (short) Math.max(current[index], previous[index]);
    }

    public int size() {
        return sea.length;
    }

    private static short[] empty(int size) {
        short[] a = new short[size];
        Arrays.fill(a, SatelliteFrame.NO_DATA);
        return a;
    }
}
