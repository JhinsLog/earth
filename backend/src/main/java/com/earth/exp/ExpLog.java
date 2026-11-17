package com.earth.exp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 경험치 정산을 Loki의 {@code stream="exp"}로 남긴다(logback-spring.xml). 값은 키-값으로 붙여 Grafana에서
 * {@code {stream="exp"} | json | userId="12"}처럼 바로 걸러 볼 수 있게 한다.
 *
 * <p>영구 기록은 DB 원장(exp_ledger)이다. Loki는 30일만 두지만(D-15) 대시보드·검색이 쉬워 최근 흐름을 보는 데 쓴다.
 * 커밋된 정산만 남기도록 정산 트랜잭션이 끝난 뒤에 부른다.
 */
public final class ExpLog {

    private static final Logger log = LoggerFactory.getLogger("earth.exp");

    private ExpLog() {
    }

    public static void settled(ExpSettlementService.Settlement s) {
        log.atInfo()
                .setMessage("exp")
                .addKeyValue("reason", ExpLedgerEntry.FLARE_SETTLED)
                .addKeyValue("userId", s.userId())
                .addKeyValue("flareId", s.flareId())
                .addKeyValue("delta", s.delta())
                .addKeyValue("expBefore", s.expBefore())
                .addKeyValue("expAfter", s.expAfter())
                .addKeyValue("seen", s.seen())
                .addKeyValue("ended", s.ended())
                .addKeyValue("disputed", s.disputed())
                .log();
    }
}
