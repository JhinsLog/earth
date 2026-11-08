package com.earth.scheduler;

import com.earth.service.FlareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TTL이 지난 플레어를 정리한다.
 *
 * <p>조회 쿼리가 이미 expiresAt으로 거르기 때문에 이 주기가 늦어도 사용자에게 만료된 플레어가
 * 보이지는 않는다. 이 스케줄러의 역할은 상태를 EXPIRED로 정리하는 것과, 이미 화면을 켜 둔
 * 클라이언트에게 플레어가 사라졌음을 실시간으로 알리는 것이다.
 */
@Component
public class FlareExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(FlareExpirationScheduler.class);

    private final FlareService flareService;

    public FlareExpirationScheduler(FlareService flareService) {
        this.flareService = flareService;
    }

    @Scheduled(fixedDelayString = "${earth.flare.expiration-scan-interval-ms:30000}")
    public void expireDueFlares() {
        int expired = flareService.expireDue();
        if (expired > 0) {
            log.info("만료된 플레어 {}개를 정리했습니다.", expired);
        }
    }
}
