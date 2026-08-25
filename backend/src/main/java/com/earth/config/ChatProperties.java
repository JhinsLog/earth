package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earth.chat")
public record ChatProperties(int minLevelToJoin) {
}
