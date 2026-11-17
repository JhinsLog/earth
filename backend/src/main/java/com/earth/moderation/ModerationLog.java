package com.earth.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신고와 검열 조치를 Loki의 {@code stream="moderation"}으로 남긴다(logback-spring.xml). 영구 기록은 DB(flare_reports,
 * moderation_actions)이고, 이쪽은 Grafana에서 최근 흐름(신고가 몰리는 사람·시간대)을 보기 위한 것이다.
 *
 * <p>커밋된 뒤에만 쓴다. 롤백된 신고(중복 등)가 로그에 남으면 분석이 틀어진다. 글 내용은 남기지 않는다 — 신고된 글은 개인정보나
 * 사람을 특정하는 위치를 담았을 수 있고, 로그는 DB보다 넓게 퍼진다.
 */
@Component
public class ModerationLog {

    private static final Logger log = LoggerFactory.getLogger("earth.moderation");

    @TransactionalEventListener
    public void on(ModerationRecorded e) {
        log.atInfo()
                .setMessage("moderation")
                .addKeyValue("event", e.event())
                .addKeyValue("flareId", e.flareId())
                .addKeyValue("actorId", e.actorId())
                .addKeyValue("reason", e.reason())
                .addKeyValue("reportCount", e.reportCount())
                .log();
    }
}
