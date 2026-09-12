package com.earth.service;

import com.earth.config.EventProperties;
import com.earth.domain.event.Event;
import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventConfirmationRepository;
import com.earth.domain.event.EventRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.EventCreateRequest;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공감 취소가 수명을 정확히 되돌리는지 검증한다.
 *
 * <p>취소가 공감 수만 줄이고 만료 시각을 그대로 두면, 취소로 기록이 지워져
 * {@code (event_id, user_id)} 유니크 제약이 다시 통과한다. 그래서 재공감 때 연장이 또 붙고,
 * 버튼을 반복해 누르면 <b>한 사람이 혼자 상한까지 밀어올릴 수 있다.</b> "여러 명이 목격한
 * 사건이 오래 남는다"는 정책이 한 사람에게 독점되는 셈이다.
 *
 * <p>취소가 기여분을 정확히 회수하면 한 사람의 순 기여가 구조적으로 1회분을 넘지 못한다.
 * 별도 장치가 필요 없다.
 */
class EventConfirmWithdrawTest extends IntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventConfirmationRepository confirmationRepository;

    @Autowired
    private EventProperties eventProperties;

    // 만료 시각을 과거로 옮기는 것은 운영 코드에 테스트용 setter를 두지 않고 SQL로 처리한다.
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        confirmationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("공감과 취소를 반복해도 한 사람의 순 기여는 1회분을 넘지 않는다")
    void repeatedConfirmAndWithdrawGrantsAtMostOneExtension() {
        User author = userRepository.save(user("author"));
        User confirmer = userRepository.save(user("confirmer"));
        long eventId = eventService.create(author, request()).id();

        Duration baseLifetime = lifetimeOf(eventId);
        Duration oneExtension = Duration.ofMinutes(eventProperties.confirmExtensionMinutes());

        for (int i = 0; i < 10; i++) {
            eventService.confirm(confirmer, eventId);
            eventService.withdrawConfirmation(confirmer, eventId);
        }
        assertThat(lifetimeOf(eventId))
                .as("공감·취소를 10번 반복한 뒤 수명이 원래대로 돌아와야 한다")
                .isEqualTo(baseLifetime);

        // 마지막으로 공감만 남긴 상태
        eventService.confirm(confirmer, eventId);
        assertThat(lifetimeOf(eventId))
                .as("반복 횟수와 무관하게 한 사람의 기여는 1회분뿐이다")
                .isEqualTo(baseLifetime.plus(oneExtension));
    }

    @Test
    @DisplayName("기본 수명이 지난 뒤 유일한 공감을 취소하면 별이 그 순간 사라진다")
    void withdrawCanExpireTheEventImmediately() {
        User author = userRepository.save(user("author"));
        User confirmer = userRepository.save(user("confirmer"));
        long eventId = eventService.create(author, request()).id();

        eventService.confirm(confirmer, eventId);

        // 기본 수명은 지나갔고 공감이 늘려준 시간으로만 살아 있는 상태를 만든다.
        // 시계를 앞으로 돌릴 수 없으므로 만료 시각을 "곧"으로 당겨 같은 상황을 흉내낸다.
        // 기록된 기여분(5분)은 그대로 남아 있다.
        jdbc.update("update events set expires_at = now() + interval '1 second' where id = ?", eventId);
        assertThat(eventRepository.findById(eventId).orElseThrow().isVisible())
                .as("공감 덕분에 아직 보이는 상태")
                .isTrue();

        eventService.withdrawConfirmation(confirmer, eventId);
        assertThat(eventRepository.findById(eventId).orElseThrow().isVisible())
                .as("기여분을 회수하면 공감이 없었을 때의 상태로 돌아간다 — 그 순간 사라진다")
                .isFalse();
    }

    private Duration lifetimeOf(long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        return Duration.between(event.getCreatedAt(), event.getExpiresAt());
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }

    private EventCreateRequest request() {
        return new EventCreateRequest("공감 취소 테스트", "내용", EventCategory.ETC, 37.5665, 126.9780);
    }
}
