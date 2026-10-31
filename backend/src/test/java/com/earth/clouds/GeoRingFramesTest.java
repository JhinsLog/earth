package com.earth.clouds;

import com.earth.config.CloudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 위성마다 최신 영상을 찾는 규칙. 가짜 서버로 "아직 없는 시각", "빈 영상이 섞여 오는 GIBS", "실패한 위성"을
 * 흉내 낸다.
 */
class GeoRingFramesTest {

    private static final int WIDTH = 16;
    private static final Instant NOW = Instant.parse("2026-10-31T04:47:00Z");
    private static final String COLORMAP = "<ColorMapEntry rgb=\"10,10,10\" value=\"(0,2]\"/>"
            .repeat(1) + java.util.stream.IntStream.range(1, 12)
            .mapToObj(i -> "<ColorMapEntry rgb=\"" + (10 + i * 20) + ",0,0\" value=\"(" + i * 10 + "," + (i * 10 + 2) + "]\"/>")
            .reduce("", String::concat);

    /** 시각별로 준비된 영상만 돌려주는 가짜 서버. */
    private static final class FakeServer implements GeoRingFrames.Fetcher {
        final Map<GeoSatellite, Set<Instant>> available = new HashMap<>();
        final Map<GeoSatellite, Integer> emptyBeforeSuccess = new HashMap<>();
        final Set<GeoSatellite> broken = new HashSet<>();
        final List<String> fullRequests = new ArrayList<>();

        @Override
        public Optional<GeoRingFrames.Fetched> fetch(GeoSatellite satellite, Instant time, int width) {
            if (broken.contains(satellite)) {
                throw new IllegalStateException("연결 실패");
            }
            if (width == WIDTH) {
                fullRequests.add(satellite + "@" + time);
            }
            boolean has = available.getOrDefault(satellite, Set.of()).contains(time);
            int[] argb = new int[width * (width / 2)];
            if (satellite.provider() == GeoSatellite.Provider.EUMETVIEW && !has) {
                return Optional.empty(); // EUMETView는 없는 시각에 XML 예외를 준다
            }
            int left = emptyBeforeSuccess.getOrDefault(satellite, 0);
            if (has && width == WIDTH && left > 0) {
                emptyBeforeSuccess.put(satellite, left - 1);
                has = false; // GIBS가 가끔 빈 영상을 주는 것
            }
            if (has) {
                java.util.Arrays.fill(argb, 0xFF0A0A0A);
            }
            return Optional.of(new GeoRingFrames.Fetched(argb, time));
        }

        @Override
        public List<Instant> availableTimes(GeoSatellite satellite, Instant from, Instant to) {
            return new ArrayList<>(available.getOrDefault(satellite, Set.of()));
        }

        @Override
        public String colormapXml() {
            return COLORMAP;
        }
    }

    private static GeoRingFrames ring(FakeServer server) {
        CloudProperties p = new CloudProperties(true, "", "", WIDTH, true, "", "", "", "");
        return new GeoRingFrames(server, p);
    }

    private static Instant at(String hhmm) {
        return Instant.parse("2026-10-31T" + hhmm + ":00Z");
    }

    @Test
    @DisplayName("GIBS는 시각 목록의 최신 시각을, EUMETView는 지금부터 거슬러 올라가 처음 있는 시각을 쓴다")
    void picksNewestAvailable() {
        FakeServer server = new FakeServer();
        server.available.put(GeoSatellite.HIMAWARI, Set.of(at("04:00"), at("04:10")));
        server.available.put(GeoSatellite.MTG, Set.of(at("04:10"), at("04:20")));
        GeoRingFrames ring = ring(server);

        assertThat(ring.refresh(NOW)).isTrue();

        assertThat(ring.current()).extracting(SatelliteFrame::satellite, SatelliteFrame::observedAt)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(GeoSatellite.HIMAWARI, at("04:10")),
                        org.assertj.core.groups.Tuple.tuple(GeoSatellite.MTG, at("04:20")));
    }

    @Test
    @DisplayName("GIBS가 빈 영상을 주면 같은 시각을 다시 묻는다")
    void retriesFlakyGibs() {
        FakeServer server = new FakeServer();
        server.available.put(GeoSatellite.GOES_WEST, Set.of(at("04:30")));
        server.emptyBeforeSuccess.put(GeoSatellite.GOES_WEST, 2);
        GeoRingFrames ring = ring(server);

        ring.refresh(NOW);

        assertThat(ring.current()).singleElement()
                .extracting(SatelliteFrame::observedAt).isEqualTo(at("04:30"));
    }

    @Test
    @DisplayName("이미 가진 영상보다 새것이 없으면 전체 크기로 받지 않고, 바뀐 것도 없다고 답한다")
    void skipsWhenNothingNewer() {
        FakeServer server = new FakeServer();
        server.available.put(GeoSatellite.MTG, Set.of(at("04:20")));
        server.available.put(GeoSatellite.HIMAWARI, Set.of(at("04:10")));
        GeoRingFrames ring = ring(server);
        ring.refresh(NOW);
        server.fullRequests.clear();

        assertThat(ring.refresh(NOW.plusSeconds(60))).isFalse();
        assertThat(server.fullRequests).isEmpty();
    }

    @Test
    @DisplayName("한 위성이 실패해도 나머지는 받고, 3시간 넘은 영상은 버린다")
    void isolatesFailuresAndDropsStale() {
        FakeServer server = new FakeServer();
        server.available.put(GeoSatellite.MTG, Set.of(at("04:20")));
        server.available.put(GeoSatellite.HIMAWARI, Set.of(at("04:10")));
        server.broken.add(GeoSatellite.GOES_EAST);
        GeoRingFrames ring = ring(server);

        ring.refresh(NOW);
        assertThat(ring.current()).hasSize(2);

        // 3시간 뒤까지 새 영상이 없으면 둘 다 빠진다.
        server.available.clear();
        assertThat(ring.refresh(at("07:30"))).isTrue();
        assertThat(ring.current()).isEmpty();
    }

    @Test
    @DisplayName("3×3 중앙값은 외딴 점 잡음을 지우고, 값 없는 칸은 그대로 두며, 경도 ±180°를 잇는다")
    void medianRemovesSpeckle() {
        int w = 8;
        int h = 4;
        short[] v = new short[w * h];
        java.util.Arrays.fill(v, (short) 100);
        v[1 * w + 3] = 900;                       // 외딴 점
        v[2 * w + 5] = SatelliteFrame.NO_DATA;    // 원판 밖
        v[1 * w] = 500;                           // 0열 점: 이웃에 마지막 열이 들어가야 지워진다

        short[] out = GeoRingFrames.median3x3(v, w, h);

        assertThat(out[1 * w + 3]).isEqualTo((short) 100);
        assertThat(out[2 * w + 5]).isEqualTo(SatelliteFrame.NO_DATA);
        assertThat(out[1 * w]).isEqualTo((short) 100);
    }
}
