package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earth.cors")
public record CorsProperties(String allowedOrigin) {
}
