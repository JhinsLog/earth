package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 발급 정책.
 *
 * <p>유효기간이 long 기본값 0으로 떨어지면 발급 즉시 만료된 토큰이 나가 로그인은 성공한 것처럼
 * 보이는데 이후 모든 요청이 401로 떨어진다. 원인을 추적하기 어려운 형태로 망가지므로
 * application.yml과 같은 값을 기본값으로 못 박아 둔다.
 *
 * @param secret                 서명 키. 운영에서는 JWT_SECRET 환경변수로 반드시 교체한다.
 * @param accessTokenValidityMs  액세스 토큰 유효기간 (기본 1시간)
 * @param refreshTokenValidityMs 리프레시 토큰 유효기간 (기본 14일)
 */
@ConfigurationProperties(prefix = "earth.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("3600000") long accessTokenValidityMs,
        @DefaultValue("1209600000") long refreshTokenValidityMs) {
}
