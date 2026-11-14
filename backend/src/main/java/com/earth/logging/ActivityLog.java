package com.earth.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Map;

/**
 * 사용자가 서버에서 실제로 한 일(로그인, 플레어 등록·수정·삭제, 공감, 채팅 전송, 차단 등)을 기록한다.
 *
 * <p>로거 이름 {@code earth.activity}는 logback-spring.xml이 Loki의 {@code stream="activity"}로 보낸다. 값은 SLF4J
 * 키-값으로 붙여 JSON 필드가 되게 한다 — 메시지 문자열에 끼워 넣으면 Grafana에서 다시 정규식으로 쪼개야 한다.
 *
 * <p>브라우저가 보내는 행동 로그({@code stream="client"})와 따로 두는 이유: 그쪽은 누구나 위조할 수 있고, 이쪽은 서버가
 * 인증과 처리 결과(상태 코드)까지 확인한 사실이다. "누가 무엇을 했는가"를 따질 때는 이쪽을 근거로 삼는다.
 *
 * <p>채팅 본문·좌표 같은 내용은 남기지 않는다. 무엇을 했는지만 알면 되고, 내용은 이미 DB에 있다. 로그에 개인 내용이
 * 복제되면 삭제 요청이 와도 지울 곳이 두 군데가 된다.
 */
public final class ActivityLog {

    private static final Logger log = LoggerFactory.getLogger("earth.activity");

    private ActivityLog() {
    }

    public static void record(Long userId, String action, Map<String, ?> fields) {
        LoggingEventBuilder event = log.atInfo()
                .setMessage("activity")
                .addKeyValue("action", action)
                .addKeyValue("userId", userId);
        fields.forEach((key, value) -> {
            if (value != null) event.addKeyValue(key, value);
        });
        event.log();
    }
}
