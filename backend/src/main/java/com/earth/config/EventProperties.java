package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 별(이벤트)의 수명·등록 정책.
 *
 * @param ttlMinutes 별이 살아있는 시간(분). 현재 30분은 임시값이다. 레벨/EXP 정책이
 *                   확정되면 레벨별로 다른 TTL을 주는 식으로 확장될 수 있어 설정으로
 *                   분리해 둔다.
 * @param maxPerHour 한 사용자가 1시간 안에 등록할 수 있는 최대 개수. 지도가 한 사람의
 *                   별로 도배되는 것을 막기 위한 장치다.
 *                   설정값이 없을 때 int 기본값 0으로 떨어지면 아무도 등록할 수 없게 되므로
 *                   반드시 기본값을 명시한다.
 * @param confirmExtensionMinutes 공감 1건이 별의 수명을 늘리는 시간(분).
 * @param maxLifetimeHours 공감이 아무리 쌓여도 별이 살아있을 수 있는 최대 시간.
 *                         생성 시각 기준이므로 공감이 계속 들어와도 영원히 남지 않는다.
 */
@ConfigurationProperties(prefix = "earth.event")
public record EventProperties(
        @DefaultValue("30") int ttlMinutes,
        @DefaultValue("5") int maxPerHour,
        @DefaultValue("15") int confirmExtensionMinutes,
        @DefaultValue("6") int maxLifetimeHours) {
}
