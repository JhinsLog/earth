package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 브라우저가 보내는 오류·행동 로그({@code POST /api/client-events})의 수용 한도.
 *
 * <p>이 API는 로그인 없이 열려 있다(오류는 로그인 전에도 난다). 그래서 누구나 아무 값이나 보낼 수 있다는 전제로
 * 한도를 둔다. 0으로 떨어지면 모든 요청이 막히거나(분당 0건) 모든 본문이 거절되므로 기본값을 명시한다.
 *
 * @param clientEventsPerMinute 한 접속지(IP)가 1분에 보낼 수 있는 묶음 수. 정상 브라우저는 10초마다 한 번 보내므로
 *                              분당 6번 남짓이고, 여러 탭·한 집 공유기 뒤의 여러 기기를 감안해 넉넉히 잡았다.
 * @param maxBodyBytes          한 묶음 본문의 최대 크기. 브라우저의 keepalive 요청 한도(64KB)와 맞췄다.
 */
@ConfigurationProperties(prefix = "earth.logs")
public record LogProperties(
        @DefaultValue("30") int clientEventsPerMinute,
        @DefaultValue("65536") long maxBodyBytes) {
}
