package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사용자 플레어 신고(D-24).
 *
 * @param hideThreshold 서로 다른 사람의 신고가 이만큼 쌓이면 운영자가 보기 전까지 임시 비공개한다. 한 사람이 혼자 가리지 못하고,
 *                      운영자가 늘 지켜볼 수 없는 규모에서도 문제 플레어가 오래 떠 있지 않을 만큼
 */
@ConfigurationProperties(prefix = "earth.moderation")
public record ModerationProperties(@DefaultValue("3") int hideThreshold) {
}
