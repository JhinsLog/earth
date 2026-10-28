package com.earth.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicAccessPolicyTest {

    @Test
    @DisplayName("비로그인: 공개 피드와 자기 접속 국가 채널만")
    void anonymous() {
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/events.public")).isTrue();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/events.country.KR")).isTrue();

        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/events")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/events.country.JP")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/chat.1")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, null, "/topic/events.country.null")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, null, "/topic/events.public")).isTrue();
    }

    @Test
    @DisplayName("로그인: 전부")
    void authenticated() {
        assertThat(TopicAccessPolicy.canSubscribe(true, null, "/topic/events")).isTrue();
        assertThat(TopicAccessPolicy.canSubscribe(true, null, "/topic/chat.1")).isTrue();
    }
}
