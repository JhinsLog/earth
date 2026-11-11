package com.earth.service;

import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserBlockRepository;
import com.earth.domain.user.UserRepository;
import com.earth.dto.BlockedUserResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 차단과 해제가 몇 번을 불러도 같은 결과를 내는지 검증한다.
 *
 * <p>차단 버튼은 두 번 눌리기 쉽고, 두 기기에서 동시에 누를 수도 있다. "있는지 보고 없으면 넣는다"로
 * 만들면 동시 요청 둘이 모두 없다고 보고 넣다가 하나가 제약 위반(500)으로 실패한다.
 */
class UserBlockServiceTest extends IntegrationTest {

    @Autowired
    private UserBlockService userBlockService;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userBlockRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("차단하면 목록에 닉네임과 함께 나오고, 해제하면 사라진다. 반복해도 결과가 같다")
    void blockAndUnblockAreIdempotent() {
        User me = userRepository.save(user("me"));
        User troll = userRepository.save(user("troll"));

        userBlockService.block(me, troll.getId());
        userBlockService.block(me, troll.getId());
        assertThat(userBlockService.findMine(me)).extracting(BlockedUserResponse::nickname).containsExactly("troll");
        assertThat(userBlockService.findMine(troll)).as("차단은 한쪽 방향이다").isEmpty();

        userBlockService.unblock(me, troll.getId());
        userBlockService.unblock(me, troll.getId());
        assertThat(userBlockService.findMine(me)).isEmpty();
    }

    @Test
    @DisplayName("같은 차단을 동시에 보내도 모두 성공하고 한 행만 남는다")
    void concurrentBlockLeavesOneRow() throws InterruptedException {
        User me = userRepository.save(user("me"));
        User troll = userRepository.save(user("troll"));
        int attempts = 10;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    userBlockService.block(me, troll.getId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        assertThat(userBlockRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("자기 자신이나 없는 사용자는 차단할 수 없다")
    void rejectsSelfAndUnknown() {
        User me = userRepository.save(user("me"));

        assertThatThrownBy(() -> userBlockService.block(me, me.getId()))
                .extracting(e -> ((EarthApiException) e).getErrorCode()).isEqualTo(ErrorCode.CANNOT_BLOCK_SELF);
        assertThatThrownBy(() -> userBlockService.block(me, 987654321L))
                .extracting(e -> ((EarthApiException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key + "@test.local", key, null);
    }
}
