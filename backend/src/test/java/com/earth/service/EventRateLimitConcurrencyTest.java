package com.earth.service;

import com.earth.config.EventProperties;
import com.earth.domain.event.EventCategory;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록 빈도 제한이 동시 요청에도 지켜지는지 검증한다.
 *
 * <p>제한 검사는 "최근 1시간 등록 수를 세고, 한도보다 적으면 저장한다"는 두 단계다. 그 사이에
 * 다른 요청이 끼어들면 여러 요청이 모두 같은 카운트를 보고 통과한다. 한 명이 요청을 동시에
 * 여러 개 보내는 것은 어렵지 않으므로, 도배를 막으려고 만든 기능이 정확히 그 방식으로 뚫린다.
 *
 * <p>손으로는 재현할 수 없다. 밀리초 단위로 요청이 겹쳐야 하고 성공 여부가 확률적이다.
 * 이 테스트가 없으면 고쳤는지 확인할 방법이 없다.
 */
class EventRateLimitConcurrencyTest extends IntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventProperties eventProperties;

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 사용자의 동시 등록 요청이 한도를 넘겨 저장되지 않는다")
    void concurrentCreateRespectsHourlyLimit() throws InterruptedException {
        User author = userRepository.save(
                new User(AuthProvider.GOOGLE, "concurrency-test", "concurrency@test.local", "동시성테스터", null));
        int limit = eventProperties.maxPerHour();
        int attempts = limit * 3;

        // 모든 스레드를 같은 순간에 출발시킨다. 순차 실행되면 이 테스트는 의미가 없다.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                int index = i;
                pool.execute(() -> {
                    try {
                        start.await();
                        eventService.create(author, request(index));
                        accepted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        // 한도 초과로 거절되는 것이 정상 동작이다.
                        rejected.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("모든 요청이 끝나야 한다").isTrue();
        }

        long stored = eventRepository.count();
        assertThat(stored)
                .as("저장된 별 개수(%d)가 한도(%d)를 넘었다. 확인과 저장 사이에 다른 요청이 끼어들었다는 뜻이다.",
                        stored, limit)
                .isLessThanOrEqualTo(limit);
        assertThat(accepted.get() + rejected.get()).isEqualTo(attempts);
    }

    private EventCreateRequest request(int index) {
        return new EventCreateRequest(
                "동시성 테스트 " + index, "내용 " + index, EventCategory.ETC, 37.5665, 126.9780);
    }
}
