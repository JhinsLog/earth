package com.earth.realtime;

import com.earth.domain.event.EventChanged;
import com.earth.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 별 상태 변경의 후속 작업을 <b>커밋이 끝난 뒤에</b> 처리한다.
 *
 * <p>여기 모아둔 두 가지는 모두 트랜잭션 안에서 하면 안 되는 일이다.
 *
 * <p><b>Redis 전파</b>는 롤백되지 않는다. 트랜잭션 안에서 내보내면 이후 단계가 실패했을 때
 * 존재하지 않는 별이 전 접속자 화면에 남는다.
 *
 * <p><b>구독자 알림</b>은 부가 기능이다. 같은 트랜잭션에 있으면 알림을 만들다 실패했을 때
 * 사용자가 쓴 별까지 사라진다. 그리고 알림 생성은 구독을 전수 조회하므로 느린데, 그만큼
 * 등록 트랜잭션이 길어지고 사용자 행 잠금도 오래 유지된다. 밖으로 빼면 둘 다 해소된다.
 *
 * <p>대가는 반대 방향의 실패다 — 커밋은 됐는데 전파나 알림이 실패하면 그 알림은 유실된다.
 * 정석은 아웃박스 패턴이지만 지금 규모에서는 과하다. 사용자가 쓴 글을 지키는 쪽이 먼저다.
 */
@Component
public class EventChangedListener {

    private final RedisMessagePublisher redisMessagePublisher;
    private final NotificationService notificationService;

    public EventChangedListener(RedisMessagePublisher redisMessagePublisher,
                                 NotificationService notificationService) {
        this.redisMessagePublisher = redisMessagePublisher;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventChanged(EventChanged changed) {
        redisMessagePublisher.publishNewEvent(changed.snapshot());
        if (changed.created()) {
            notificationService.notifySubscribers(changed.snapshot().id());
        }
    }
}
