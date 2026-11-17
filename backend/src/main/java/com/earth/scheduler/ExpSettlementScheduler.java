package com.earth.scheduler;

import com.earth.exp.ExpLog;
import com.earth.exp.ExpSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 끝난 플레어의 경험치를 주기적으로 정산한다(D-21).
 *
 * <p>플레어마다 따로 트랜잭션을 연다. 한 번에 묶으면 하나가 실패할 때 전부 되돌아가고, 잠금을 오래 쥔다. 실패한 플레어는
 * settled_at이 비어 있으니 다음 주기에 다시 집힌다. 로그(Loki)는 커밋된 정산만 남기도록 트랜잭션이 끝난 뒤에 쓴다.
 */
@Component
@ConditionalOnProperty(prefix = "earth.exp", name = "settlement-enabled", havingValue = "true", matchIfMissing = true)
public class ExpSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpSettlementScheduler.class);
    /** 한 주기에 정산할 최대 수. 밀려 있어도 한 주기가 길어지지 않게 나눈다. */
    private static final int BATCH = 200;

    private final ExpSettlementService settlementService;

    public ExpSettlementScheduler(ExpSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Scheduled(fixedDelayString = "${earth.exp.settlement-interval-ms:60000}", initialDelay = 40_000)
    public void settleDue() {
        int settled = 0;
        for (Long flareId : settlementService.findDue(BATCH)) {
            try {
                var result = settlementService.settle(flareId);
                if (result.isPresent()) {
                    ExpLog.settled(result.get());
                    settled++;
                }
            } catch (RuntimeException e) {
                log.warn("플레어 {} 경험치 정산 실패. 다음 주기에 다시 시도합니다: {}", flareId, e.getMessage());
            }
        }
        if (settled > 0) {
            log.info("경험치 정산: 플레어 {}개", settled);
        }
    }
}
