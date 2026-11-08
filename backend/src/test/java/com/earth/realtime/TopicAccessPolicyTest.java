package com.earth.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicAccessPolicyTest {

    @Test
    @DisplayName("비로그인: 공개 피드와 자기 접속 국가 채널만")
    void anonymous() {
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/flares.public")).isTrue();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/flares.country.KR")).isTrue();

        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/flares")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/flares.country.JP")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, "KR", "/topic/chat.1")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, null, "/topic/flares.country.null")).isFalse();
        assertThat(TopicAccessPolicy.canSubscribe(false, null, "/topic/flares.public")).isTrue();
    }

    @Test
    @DisplayName("로그인: 전부")
    void authenticated() {
        assertThat(TopicAccessPolicy.canSubscribe(true, null, "/topic/flares")).isTrue();
        assertThat(TopicAccessPolicy.canSubscribe(true, null, "/topic/chat.1")).isTrue();
    }
}
