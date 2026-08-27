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
 */
@ConfigurationProperties(prefix = "earth.chat")
public record ChatProperties(
        @DefaultValue("1") int minLevelToJoin,
        @DefaultValue("20") int maxPerMinute) {
}
