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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서로 다른 사람들이 동시에 공감해도 공감 수와 수명이 유실되지 않는지 검증한다.
 *
 * <p>{@code confirm_count}와 {@code expires_at}은 둘 다 "읽어서 계산하고 쓴다". 잠그지 않으면
 * 동시에 들어온 공감이 서로를 덮어쓴다(lost update) — 공감 기록은 N건 남는데 카운터는 1만
 * 오른다.
 *
 * <p>카운터가 하나 틀리는 것보다 <b>수명 연장이 함께 유실되는 것</b>이 문제다. "확인되지 않은
 * 별은 스스로 소멸하고 여러 명이 목격한 사건은 오래 남는다"가 이 서비스의 전제인데, 사람이
 * 몰려 동시 공감이 겹칠수록 그 전제가 어긋난다. 하필 가장 중요한 별에서 틀어진다.
 */
class EventConfirmConcurrencyTest extends IntegrationTest {

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

    @AfterEach
    void tearDown() {
        confirmationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("서로 다른 사람들이 동시에 공감하면 공감 수와 수명이 그만큼 늘어난다")
    void concurrentConfirmsAreNotLost() throws InterruptedException {
        User author = userRepository.save(user("author"));
        long eventId = eventService.create(author, request()).id();
        // 작성자는 자기 별에 공감할 수 없으므로 공감할 사람을 따로 만든다.
        int confirmers = 10;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < confirmers; i++) {
            users.add(userRepository.save(user("confirmer-" + i)));
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(confirmers);

        try (ExecutorService pool = Executors.newFixedThreadPool(confirmers)) {
            for (User confirmer : users) {
                pool.execute(() -> {
                    try {
                        start.await();
                        eventService.confirm(confirmer, eventId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("모든 공감이 끝나야 한다").isTrue();
        }

        Event reloaded = eventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getConfirmCount())
                .as("공감 %d건이 들어왔는데 카운터가 %d다. 동시 갱신이 서로를 덮어썼다는 뜻이다.",
                        confirmers, reloaded.getConfirmCount())
                .isEqualTo(confirmers);
        assertThat(confirmationRepository.count()).isEqualTo(confirmers);

        // 수명도 공감 수만큼 늘어나야 한다. 기본 TTL + (공감 수 × 연장분).
        Duration expectedExtension = Duration.ofMinutes(
                (long) eventProperties.confirmExtensionMinutes() * confirmers);
        Duration actual = Duration.between(reloaded.getCreatedAt(), reloaded.getExpiresAt());
        Duration expected = Duration.ofMinutes(eventProperties.ttlMinutes()).plus(expectedExtension);
        assertThat(actual)
                .as("수명 연장이 유실됐다. 기대 %s, 실제 %s", expected, actual)
                .isEqualTo(expected);
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }

    private EventCreateRequest request() {
        return new EventCreateRequest("동시 공감 테스트", "내용", EventCategory.ETC, 37.5665, 126.9780);
    }
}
