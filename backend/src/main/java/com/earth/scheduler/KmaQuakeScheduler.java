package com.earth.scheduler;

import com.earth.config.KmaQuakeProperties;
import com.earth.quake.KmaQuakeClient;
import com.earth.quake.Quake;
import com.earth.quake.QuakeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 기상청 지진통보문을 1분마다 받아 {@link QuakeStore}를 바꾼다.
 *
 * <p>실패하면 경고만 남기고 다음 주기를 기다린다. 저장소는 그대로라 마지막으로 받은 목록이 계속 보인다.
 * 1분마다 도는 작업이라 성공 로그는 목록이 바뀔 때만 남긴다(하루 1,440줄이 쌓이지 않게).
 */
@Component
@ConditionalOnProperty(prefix = "earth.quakes.kma", name = "enabled", havingValue = "true")
public class KmaQuakeScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmaQuakeScheduler.class);

    private final KmaQuakeClient client;
    private final QuakeStore store;

    public KmaQuakeScheduler(KmaQuakeClient client, QuakeStore store, KmaQuakeProperties properties) {
        // 켜 두고 키를 빠뜨리면 기동을 막는다. 조용히 빈 키로 돌면 매 주기 오류만 쌓이고 아무도 모른다.
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("earth.quakes.kma.api-key(KMA_OPENAPI_KEY)가 비어 있습니다.");
        }
        this.client = client;
        this.store = store;
    }

    @Scheduled(fixedRateString = "${earth.quakes.kma.refresh-interval-ms:60000}", initialDelay = 5_000)
    public void refresh() {
        long started = System.nanoTime();
        try {
            Instant now = Instant.now();
            List<Quake> quakes = client.fetch(now);
            if (store.replace(quakes, now)) {
                // 통보종류 코드별 건수. 코드 뜻은 공식 문서에 없어 운영에서 실제 분포를 보고 확인한다.
                Map<Integer, Long> byType = quakes.stream()
                        .collect(Collectors.groupingBy(Quake::noticeType, TreeMap::new, Collectors.counting()));
                log.info("기상청 지진정보 갱신: 지진 {}건(통보종류별 {}), {}ms", quakes.size(), byType,
                        (System.nanoTime() - started) / 1_000_000);
            }
        } catch (RuntimeException e) {
            log.warn("기상청 지진정보 수집 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
