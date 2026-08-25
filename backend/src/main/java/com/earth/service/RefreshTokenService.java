package com.earth.service;

import com.earth.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis에 사용자별 최신 refresh token 하나만 유지해 재발급 시 회전(rotation)/폐기를 지원한다. */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "earth:refresh-token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId, refreshToken, Duration.ofMillis(jwtProperties.refreshTokenValidityMs()));
    }

    public boolean matches(Long userId, String refreshToken) {
        return refreshToken.equals(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    public void revoke(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
