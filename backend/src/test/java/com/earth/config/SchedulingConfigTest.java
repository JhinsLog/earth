package com.earth.config;

import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Scheduled} 작업이 WebSocket 브로커 스케줄러(MessageBroker-*)에서 돌지 않는지 확인한다.
 * 전용 풀이 없으면 브로커 설정이 등록한 스케줄러가 대신 쓰인다.
 */
@Import(SchedulingConfigTest.ThreadProbe.class)
class SchedulingConfigTest extends IntegrationTest {

    @TestConfiguration
    static class ThreadProbe {
        final CompletableFuture<String> threadName = new CompletableFuture<>();

        @Scheduled(fixedDelay = 3_600_000)
        void record() {
            threadName.complete(Thread.currentThread().getName());
        }
    }

    @Autowired
    private ThreadProbe probe;

    @Test
    @DisplayName("@Scheduled 작업은 전용 스레드 풀에서 돈다")
    void scheduledTasksRunOnDedicatedPool() throws Exception {
        assertThat(probe.threadName.get(10, TimeUnit.SECONDS)).startsWith("earth-scheduled-");
    }
}
