package com.earth.service;

import com.earth.config.ChatProperties;
import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 내용을 반복해 보내는 도배를 막는지 검증한다.
 *
 * <p>"세고, 한도보다 적으면 통과"를 따로 하면 동시 요청이 모두 같은 수를 보고 통과한다.
 * 카운터 증가가 원자적이라 동시 요청에서도 정확히 한도만큼만 저장되는지, 그리고 증가와 함께
 * 만료 시간이 반드시 걸리는지(만료 없는 키 = 영구 차단)를 본다.
 */
class ChatSameContentLimitTest extends IntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FlareService flareService;

    @Autowired
    private ChatProperties chatProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 내용은 정해진 횟수까지만 받고, 공백·대소문자만 바꾼 것도 같은 내용으로 센다")
    void rejectsRepeatedContentIncludingTrivialVariants() {
        User sender = userRepository.save(user("sender"));
        long flareId = flareService.create(sender, request()).id();
        int max = chatProperties.maxSameContent();

        for (int i = 0; i < max; i++) {
            chatService.postMessage(flareId, sender, "Hello   World");
        }
        assertThatThrownBy(() -> chatService.postMessage(flareId, sender, "  hello world "))
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_DUPLICATE_CONTENT);

        // 다른 내용은 영향을 받지 않는다.
        chatService.postMessage(flareId, sender, "다른 내용");
        assertThat(chatMessageRepository.count()).isEqualTo(max + 1);
    }

    @Test
    @DisplayName("같은 내용을 동시에 보내도 정해진 횟수만큼만 저장된다")
    void concurrentSameContentRespectsLimit() throws InterruptedException {
        User sender = userRepository.save(user("racer"));
        long flareId = flareService.create(sender, request()).id();
        int attempts = 10;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    chatService.postMessage(flareId, sender, "동시에 같은 말");
                } catch (EarthApiException e) {
                    if (e.getErrorCode() == ErrorCode.CHAT_DUPLICATE_CONTENT) rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(chatMessageRepository.count()).isEqualTo(chatProperties.maxSameContent());
        assertThat(rejected.get()).isEqualTo(attempts - chatProperties.maxSameContent());
    }

    @Test
    @DisplayName("카운터 키에는 항상 만료 시간이 걸려 있다")
    void countersAlwaysExpire() {
        User sender = userRepository.save(user("ttl"));
        long flareId = flareService.create(sender, request()).id();
        chatService.postMessage(flareId, sender, "만료 확인");

        Set<String> keys = redis.keys("earth:chat-*:" + sender.getId() + "*");
        assertThat(keys).as("도배 제한 키와 같은 내용 키").hasSize(2);
        for (String key : keys) {
            assertThat(redis.getExpire(key)).as(key).isPositive();
        }
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }

    private FlareCreateRequest request() {
        return new FlareCreateRequest("반복 차단 테스트", "내용", FlareCategory.ETC, 37.5665, 126.9780);
    }
}
