package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이벤트(별) 등록 정책.
 *
 * @param maxPerHour 한 사용자가 1시간 안에 등록할 수 있는 최대 개수. 지도가 한 사람의
 *                   별로 도배되는 것을 막기 위한 장치다.
 *                   설정값이 없을 때 int 기본값 0으로 떨어지면 아무도 등록할 수 없게 되므로
 *                   반드시 기본값을 명시한다.
 */
@ConfigurationProperties(prefix = "earth.event")
public record EventProperties(@DefaultValue("5") int maxPerHour) {
}
