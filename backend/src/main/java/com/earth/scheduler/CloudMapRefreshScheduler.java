package com.earth.scheduler;

import com.earth.clouds.CloudMap;
import com.earth.clouds.CloudMapStore;
import com.earth.clouds.GmgsiClient;
import com.earth.clouds.GmgsiFiles;
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
import java.util.Optional;

/**
 * 홈 지구본의 실시간 구름을 갱신한다.
 *
 * <p>원본은 1시간에 한 장이지만 올라오는 시각이 일정하지 않아 10분마다 목록만 확인하고, 새 파일일 때만
 * 받는다. 실패하면 경고만 남기고 이전 이미지를 계속 쓴다. 구름은 장식이라 몇 시간 늦어도 서비스에는
 * 영향이 없고, 한 번도 성공하지 못했으면 프론트가 정적 구름으로 대신한다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.clouds", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudMapRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(CloudMapRefreshScheduler.class);

    private final GmgsiClient client;
    private final CloudMapStore store;
    private final CloudProperties properties;

    public CloudMapRefreshScheduler(GmgsiClient client, CloudMapStore store, CloudProperties properties) {
        this.client = client;
        this.store = store;
        this.properties = properties;
    }

    // 기동 직후 한 번 돈다(initialDelay 0). 만료 스케줄러와 스레드를 나눠 쓰지 않게 풀을 2로 둔다(application.yml).
    @Scheduled(fixedDelayString = "${earth.clouds.refresh-interval-ms:600000}", initialDelay = 0)
    public void refresh() {
        try {
            Optional<String> latest = client.latestKey(Instant.now());
            if (latest.isEmpty()) {
                log.warn("최근 세 시간 안에 GMGSI 구름 파일이 없습니다.");
                return;
            }
            String key = latest.get();
            if (store.current().map(CloudMap::sourceKey).filter(key::equals).isPresent()) {
                return;
            }
            long started = System.nanoTime();
            Path file = client.download(key);
            try {
                byte[] jpeg = GmgsiFiles.renderJpeg(file, properties.width());
                Instant observedAt = GmgsiFiles.observedAt(key).orElse(Instant.now());
                store.replace(new CloudMap(jpeg, observedAt, key));
                log.info("구름 이미지를 갱신했습니다. 관측 {}, {}KB, {}ms",
                        observedAt, jpeg.length / 1024, (System.nanoTime() - started) / 1_000_000);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("구름 이미지 갱신 실패. 이전 이미지를 계속 씁니다: {}", e.toString());
        }
    }
}
