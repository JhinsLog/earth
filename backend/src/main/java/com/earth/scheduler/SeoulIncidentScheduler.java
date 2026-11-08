package com.earth.scheduler;

import com.earth.config.SeoulIncidentProperties;
import com.earth.incident.SeoulIncidentClient;
import com.earth.incident.SeoulIncidents;
import com.earth.service.ExternalFlareSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 서울시 실시간 돌발정보를 주기적으로 받아 플레어와 맞춘다.
 *
 * <p>실패하면 경고만 남기고 다음 주기를 기다린다. 이미 떠 있는 플레어는 그대로 두고, 동기화가 계속 실패하면 각 플레어의 만료
 * 시각(목록에 있는 동안 계속 미뤄지던 값)이 지나 스스로 사라진다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.incidents.seoul", name = "enabled", havingValue = "true")
public class SeoulIncidentScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeoulIncidentScheduler.class);

    private final SeoulIncidentClient client;
    private final ExternalFlareSyncService syncService;
    private final Duration listedGrace;

    public SeoulIncidentScheduler(SeoulIncidentClient client, ExternalFlareSyncService syncService,
                                  SeoulIncidentProperties properties) {
        // 켜 두고 키를 빠뜨리면 기동을 막는다. 조용히 빈 키로 돌면 매 주기 오류만 쌓이고 아무도 모른다.
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("earth.incidents.seoul.api-key(SEOUL_OPENAPI_KEY)가 비어 있습니다.");
        }
        this.client = client;
        this.syncService = syncService;
        // 목록에 있는 플레어는 적어도 수집 두 번 뒤까지 산다. 한 번 실패해도 꺼지지 않는다.
        this.listedGrace = Duration.ofMillis(properties.refreshIntervalMs() * 2);
    }

    @Scheduled(fixedRateString = "${earth.incidents.seoul.refresh-interval-ms:600000}", initialDelay = 20_000)
    public void refresh() {
        long started = System.nanoTime();
        try {
            SeoulIncidentClient.Snapshot snapshot = client.fetch();
            SeoulIncidents.Mapped mapped = SeoulIncidents.toDrafts(snapshot.incidents());
            ExternalFlareSyncService.Result result = syncService.sync(SeoulIncidents.SOURCE, SeoulIncidents.ACCOUNT,
                    mapped.drafts(), snapshot.complete(), listedGrace, mapped.skipped());
            log.info("서울시 돌발정보 동기화: 받은 {}건(완전한 목록 {}), 생성 {} · 갱신 {} · 종료 {} · 제외 {}, {}ms",
                    snapshot.incidents().size(), snapshot.complete(), result.created(), result.updated(),
                    result.ended(), result.skipped(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("서울시 돌발정보 동기화 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
