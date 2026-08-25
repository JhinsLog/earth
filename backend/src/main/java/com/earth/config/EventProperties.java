package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 별(이벤트)의 수명 정책.
 *
 * <p>현재 TTL 30분은 임시값이다. 레벨/EXP 정책이 확정되면 레벨별로 다른 TTL을 주는 식으로
 * 확장될 수 있어 설정으로 분리해 둔다.
 */
@ConfigurationProperties(prefix = "earth.event")
public record EventProperties(int ttlMinutes) {
}
