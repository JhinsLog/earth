package com.earth.scheduler;

import com.earth.clouds.CloudComposer;
import com.earth.clouds.CloudMap;
import com.earth.clouds.CloudMapRenderer;
import com.earth.clouds.CloudMapStore;
import com.earth.clouds.GeoRingFrames;
import com.earth.clouds.GeoSatellite;
import com.earth.clouds.GmgsiClient;
import com.earth.clouds.GmgsiFiles;
import com.earth.clouds.SatelliteFrame;
import com.earth.config.CloudProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 홈 지구본의 실시간 구름을 갱신한다.
 *
 * <p>두 가지를 받아 합친다.
 * <ul>
 *   <li>정지궤도 위성 다섯 기(10분 간격) — 구름의 본체. {@link GeoRingFrames}</li>
 *   <li>NOAA GMGSI(1시간 간격) — 위성 하나가 빠졌을 때 그 지역을 채우는 바탕. 다섯 기가 모두 실패하면
 *       이것만으로 그린다.</li>
 * </ul>
 * 10분마다 둘 다 새 자료가 있는지 보고, 하나라도 바뀌었을 때만 다시 합성한다. 실패하면 경고만 남기고 이전
 * 이미지를 계속 쓴다. 구름은 장식이라 늦어도 서비스에는 영향이 없고, 한 번도 성공하지 못했으면 프론트가
 * 정적 구름으로 대신한다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.clouds", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudMapRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(CloudMapRefreshScheduler.class);

    private final GmgsiClient gmgsiClient;
    private final GeoRingFrames ring;
    private final CloudMapStore store;
    private final CloudProperties properties;

    // 스케줄러는 fixedDelay라 앞 실행이 끝나야 다음이 돈다. 이 필드들은 그 한 스레드만 만진다.
    private byte[] gmgsiGray;
    private String gmgsiKey;
    private Instant gmgsiObservedAt;

    public CloudMapRefreshScheduler(GmgsiClient gmgsiClient, GeoRingFrames ring, CloudMapStore store,
                                    CloudProperties properties) {
        this.gmgsiClient = gmgsiClient;
        this.ring = ring;
        this.store = store;
        this.properties = properties;
    }

    // 기동 직후 한 번 돈다(initialDelay 0). 스레드 풀은 SchedulingConfig.
    @Scheduled(fixedDelayString = "${earth.clouds.refresh-interval-ms:600000}", initialDelay = 0)
    public void refresh() {
        Instant now = Instant.now();
        long started = System.nanoTime();
        boolean changed = refreshGmgsi(now);
        if (properties.ringEnabled()) {
            changed |= ring.refresh(now);
        }
        if (changed) {
            publish(started);
        }
    }

    private boolean refreshGmgsi(Instant now) {
        try {
            Optional<String> latest = gmgsiClient.latestKey(now);
            if (latest.isEmpty()) {
                log.warn("최근 세 시간 안에 GMGSI 구름 파일이 없습니다.");
                return false;
            }
            String key = latest.get();
            if (key.equals(gmgsiKey)) {
                return false;
            }
            Path file = gmgsiClient.download(key);
            try {
                gmgsiGray = GmgsiFiles.renderGray(file, properties.width());
                gmgsiKey = key;
                gmgsiObservedAt = GmgsiFiles.observedAt(key).orElse(now);
                return true;
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("GMGSI 갱신 실패. 이전 자료를 계속 씁니다: {}", e.toString());
            return false;
        }
    }

    private void publish(long started) {
        try {
            List<SatelliteFrame> frames = properties.ringEnabled() ? ring.current() : List.of();
            if (frames.isEmpty() && gmgsiGray == null) {
                return;
            }
            int width = properties.width();
            CloudComposer.Result result = CloudComposer.compose(width, frames, gmgsiGray);

            List<String> sources = new ArrayList<>();
            List<Instant> times = new ArrayList<>();
            if (frames.stream().anyMatch(f -> f.satellite().provider() == GeoSatellite.Provider.GIBS)) {
                sources.add("NASA GIBS");
            }
            if (frames.stream().anyMatch(f -> f.satellite().provider() == GeoSatellite.Provider.EUMETVIEW)) {
                sources.add("EUMETSAT");
            }
            frames.forEach(f -> times.add(f.observedAt()));
            // GMGSI가 실제로 화면에 쓰였을 때만 출처·시각에 넣는다(0.5% 미만은 위성 원판 가장자리의 틈).
            if (gmgsiGray != null && (frames.isEmpty() || result.fallbackShare() > 0.005)) {
                sources.add("NOAA GMGSI");
                times.add(gmgsiObservedAt);
            }
            Instant observedAt = times.stream().min(Comparator.naturalOrder()).orElseThrow();
            byte[] jpeg = CloudMapRenderer.encodeJpeg(result.gray(), width, width / 2);
            store.replace(new CloudMap(jpeg, observedAt, List.copyOf(sources)));

            String satellites = frames.stream()
                    .sorted(Comparator.comparing(SatelliteFrame::satellite))
                    .map(f -> f.satellite().label() + " " + f.observedAt())
                    .collect(Collectors.joining(", "));
            String calibrations = result.calibrations().entrySet().stream()
                    .map(e -> String.format("%s %.3f×밝기%+.1f(%s, RMS %.1f°C)", e.getKey().label(),
                            e.getValue().slope(), e.getValue().intercept(),
                            e.getValue().fitted() ? e.getValue().samples() + "칸 맞춤" : "기본값",
                            e.getValue().rmsCelsius()))
                    .collect(Collectors.joining(", "));
            log.info("구름 이미지를 갱신했습니다. 위성 [{}], GMGSI {} 채움 {}%, 환산 [{}], {}KB, {}ms",
                    satellites, gmgsiObservedAt, String.format("%.1f", result.fallbackShare() * 100),
                    calibrations, jpeg.length / 1024, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("구름 이미지 합성 실패. 이전 이미지를 계속 씁니다: {}", e.toString());
        }
    }
}
