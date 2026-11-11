package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 채팅 참여 정책.
 *
 * @param minLevelToJoin 채팅방에 입장해 메시지를 보낼 수 있는 최소 레벨.
 *                       설정값이 없을 때 int 기본값 0으로 떨어지면 레벨 제한이 조용히
 *                       사라지므로(모든 레벨이 0 이상) 반드시 기본값을 명시한다.
 * @param maxPerMinute   한 사용자가 1분 안에 보낼 수 있는 메시지 수. 채팅방 도배를 막는다.
 * @param maxSameContent 같은 사용자가 같은 내용을 보낼 수 있는 횟수. 넘으면 그 내용은 거절한다.
 *                       0으로 떨어지면 첫 메시지부터 막히므로 기본값을 명시한다.
 * @param sameContentWindowMinutes 같은 내용 횟수를 세는 기간(분).
 */
@ConfigurationProperties(prefix = "earth.chat")
public record ChatProperties(
        @DefaultValue("1") int minLevelToJoin,
        @DefaultValue("20") int maxPerMinute,
        @DefaultValue("2") int maxSameContent,
        @DefaultValue("10") int sameContentWindowMinutes) {
}
