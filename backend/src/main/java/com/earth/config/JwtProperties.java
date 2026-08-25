package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earth.jwt")
public record JwtProperties(String secret, long accessTokenValidityMs, long refreshTokenValidityMs) {
}
