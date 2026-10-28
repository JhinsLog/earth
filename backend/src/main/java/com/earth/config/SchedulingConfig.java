package com.earth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * {@code @Scheduled} 작업 전용 스레드 풀.
 *
 * <p>WebSocket 브로커 설정이 {@code messageBrokerTaskScheduler} 빈을 등록하기 때문에, 이것이 없으면
 * Spring Boot 기본 스케줄러가 물러나고 모든 {@code @Scheduled} 작업이 브로커 스케줄러에서 돈다
 * (운영 로그의 스레드 이름이 MessageBroker-1이었다). 그 상태에서는 {@code spring.task.scheduling.pool.size}도
 * 먹지 않는다. 구름 수집(다운로드와 변환에 수 초)이 실시간 메시징 스레드를 붙잡지 않게 분리한다.
 * 2개면 구름 수집 중에도 별 만료 스케줄러가 기다리지 않는다.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("earth-scheduled-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
