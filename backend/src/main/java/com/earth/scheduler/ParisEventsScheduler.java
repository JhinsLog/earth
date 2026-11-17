package com.earth.scheduler;

import com.earth.domain.flare.FlareOrigin;
import com.earth.config.ParisEventsProperties;
import com.earth.events.ParisEvent;
import com.earth.events.ParisEvents;
import com.earth.events.ParisEventsClient;
import com.earth.service.ExternalFlareSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 파리 행사를 주기적으로 받아 장소별 플레어와 맞춘다. 실패하면 경고만 남기고, 떠 있는 플레어는 만료 시각(오늘 마지막 회차가
 * 끝나는 시각)에 스스로 내려간다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.events.paris", name = "enabled", havingValue = "true")
public class ParisEventsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParisEventsScheduler.class);

    private final ParisEventsClient client;
    private final ExternalFlareSyncService syncService;
    private final ParisEventsProperties properties;
    private final Duration listedGrace;

    public ParisEventsScheduler(ParisEventsClient client, ExternalFlareSyncService syncService,
                                ParisEventsProperties properties) {
        this.client = client;
        this.syncService = syncService;
        this.properties = properties;
        // 목록에 있는 장소는 적어도 다음 수집까지 산다. 한 번 실패해도 바로 꺼지지 않는다.
        this.listedGrace = Duration.ofMillis(properties.refreshIntervalMs() + 600_000);
    }

    @Scheduled(fixedRateString = "${earth.events.paris.refresh-interval-ms:3600000}", initialDelay = 50_000)
    public void refresh() {
        long started = System.nanoTime();
        try {
            List<ParisEvent> events = client.fetch();
            List<ExternalFlareSyncService.Draft> drafts = ParisEvents.toDrafts(events, Instant.now(), properties.maxVenues());
            ExternalFlareSyncService.Result result = syncService.sync(ParisEvents.SOURCE, FlareOrigin.FESTIVAL, ParisEvents.ACCOUNT, drafts,
                    true, listedGrace, 0);
            log.info("파리 행사 동기화: 행사 {}건 → 장소 {}곳, 생성 {} · 갱신 {} · 종료 {}, {}ms", events.size(), drafts.size(),
                    result.created(), result.updated(), result.ended(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("파리 행사 동기화 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
