package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earth.oauth2")
public record OAuth2Properties(String redirectUrl) {
}
