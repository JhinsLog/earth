package com.earth.scheduler;

import com.earth.quake.Quake;
import com.earth.quake.QuakeStore;
import com.earth.quake.UsgsQuakeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** USGS 해외 지진을 주기적으로 받아 {@link QuakeStore}의 해외 목록을 바꾼다. 실패하면 마지막 목록을 둔다. */
@Component
@ConditionalOnProperty(prefix = "earth.quakes.usgs", name = "enabled", havingValue = "true")
public class UsgsQuakeScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsgsQuakeScheduler.class);

    private final UsgsQuakeClient client;
    private final QuakeStore store;

    public UsgsQuakeScheduler(UsgsQuakeClient client, QuakeStore store) {
        this.client = client;
        this.store = store;
    }

    @Scheduled(fixedRateString = "${earth.quakes.usgs.refresh-interval-ms:120000}", initialDelay = 15_000)
    public void refresh() {
        try {
            List<Quake> quakes = client.fetch();
            if (store.replaceOverseas(quakes, Instant.now())) {
                log.info("USGS 지진 갱신: {}건", quakes.size());
            }
        } catch (RuntimeException e) {
            log.warn("USGS 지진 수집 실패. 다음 주기에 다시 시도합니다: {}", e.getMessage());
        }
    }
}
