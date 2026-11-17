package com.earth.scheduler;

import com.earth.domain.flare.FlareOrigin;
import com.earth.service.ExternalFlareSyncService;
import com.earth.ski.SkiResorts;
import com.earth.ski.SkiSeason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 스키장 개장 정보(resources/ski/season.json)를 날짜에 맞춰 플레어로 띄우고 내린다.
 *
 * <p>파일은 기동할 때 한 번 읽는다. 파일이 바뀌면 push → 새 이미지로 교체되며 다시 읽힌다. 잘못 적힌 파일은 기동을 막는다.
 *
 * <p>목록은 늘 전체를 알고 있으므로(complete=true) 띄울 날이 지난 스키장은 다음 주기에 끝난다. 주기는 1시간이라
 * 날짜가 바뀐 뒤 늦어도 1시간 안에 "개장 예정"이 "개장"으로 바뀐다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.ski", name = "enabled", havingValue = "true")
public class SkiSeasonScheduler {

    private static final Logger log = LoggerFactory.getLogger(SkiSeasonScheduler.class);
    private static final String SEASON_FILE = "ski/season.json";

    private final ExternalFlareSyncService syncService;
    private final SkiSeason season;
    private final Duration listedGrace;
    // 개장일은 한국 날짜다. 서버 시간대(UTC)로 날짜를 세면 오전 9시까지 하루 늦게 바뀐다.
    private final Clock clock = Clock.system(SkiResorts.KST);

    public SkiSeasonScheduler(ExternalFlareSyncService syncService,
                              @Value("${earth.ski.refresh-interval-ms:3600000}") long refreshIntervalMs) {
        this.syncService = syncService;
        this.season = load();
        this.listedGrace = Duration.ofMillis(refreshIntervalMs * 2);
        long dated = season.resorts().stream().filter(r -> r.opening() != null).count();
        log.info("스키장 개장 정보 {} 시즌: 스키장 {}곳 중 개장일 발표 {}곳", season.season(), season.resorts().size(), dated);
    }

    private static SkiSeason load() {
        try (InputStream in = new ClassPathResource(SEASON_FILE).getInputStream()) {
            return SkiSeason.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(SEASON_FILE + "를 읽지 못했습니다.", e);
        }
    }

    @Scheduled(fixedRateString = "${earth.ski.refresh-interval-ms:3600000}", initialDelay = 30_000)
    public void refresh() {
        try {
            List<ExternalFlareSyncService.Draft> drafts = SkiResorts.toDrafts(season, LocalDate.now(clock));
            ExternalFlareSyncService.Result result = syncService.sync(SkiResorts.SOURCE, FlareOrigin.FESTIVAL, SkiResorts.ACCOUNT,
                    drafts, true, listedGrace, 0);
            if (result.created() + result.updated() + result.ended() > 0) {
                log.info("스키장 개장 정보 동기화: 표시 {}곳, 생성 {} · 갱신 {} · 종료 {}",
                        drafts.size(), result.created(), result.updated(), result.ended());
            }
        } catch (RuntimeException e) {
            log.warn("스키장 개장 정보 동기화 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
