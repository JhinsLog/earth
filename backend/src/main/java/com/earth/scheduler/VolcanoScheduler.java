package com.earth.scheduler;

import com.earth.config.VolcanoProperties;
import com.earth.domain.flare.FlareOrigin;
import com.earth.service.ExternalFlareSyncService;
import com.earth.volcano.VolcanoReport;
import com.earth.volcano.VolcanoReportClient;
import com.earth.volcano.VolcanoReports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 주간 화산 보고를 주기적으로 받아 화산별 공공 정보 플레어와 맞춘다. 실패하면 경고만 남기고, 떠 있는 플레어는 보고 발행 뒤
 * {@code max-report-age-days}가 되면 스스로 내려간다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.volcanoes", name = "enabled", havingValue = "true")
public class VolcanoScheduler {

    private static final Logger log = LoggerFactory.getLogger(VolcanoScheduler.class);

    private final VolcanoReportClient client;
    private final ExternalFlareSyncService syncService;
    private final Duration maxAge;
    private final Duration listedGrace;

    public VolcanoScheduler(VolcanoReportClient client, ExternalFlareSyncService syncService, VolcanoProperties properties) {
        this.client = client;
        this.syncService = syncService;
        this.maxAge = Duration.ofDays(properties.maxReportAgeDays());
        // 목록에 있는 화산은 적어도 다음 수집까지 산다. 한 번 실패해도 바로 꺼지지 않는다.
        this.listedGrace = Duration.ofMillis(properties.refreshIntervalMs() * 2);
    }

    @Scheduled(fixedRateString = "${earth.volcanoes.refresh-interval-ms:21600000}", initialDelay = 70_000)
    public void refresh() {
        try {
            List<VolcanoReport> reports = client.fetch();
            List<ExternalFlareSyncService.Draft> drafts = VolcanoReports.toDrafts(reports, Instant.now(), maxAge);
            ExternalFlareSyncService.Result result = syncService.sync(VolcanoReports.SOURCE, FlareOrigin.PUBLIC,
                    VolcanoReports.ACCOUNT, drafts, true, listedGrace, reports.size() - drafts.size());
            log.info("화산 보고 동기화: 분화 {}곳 중 표시 {}곳(한반도 인근·오래된 보고 제외 {}), 생성 {} · 갱신 {} · 종료 {}",
                    reports.size(), drafts.size(), result.skipped(), result.created(), result.updated(), result.ended());
        } catch (RuntimeException e) {
            log.warn("화산 보고 동기화 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
