package com.earth.service;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventRepository;
import com.earth.domain.notification.Notification;
import com.earth.domain.notification.NotificationRepository;
import com.earth.domain.subscription.RegionSubscription;
import com.earth.domain.subscription.RegionSubscriptionRepository;
import com.earth.domain.user.User;
import com.earth.dto.NotificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final RegionSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final EventRepository eventRepository;

    public NotificationService(RegionSubscriptionRepository subscriptionRepository,
                                NotificationRepository notificationRepository,
                                EventRepository eventRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationRepository = notificationRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * 새 이벤트 위치를 커버하는 구독을 가진 사용자들에게 알림 레코드를 남긴다.
     *
     * <p>별 등록 트랜잭션이 <b>커밋된 뒤</b>에 호출된다. 그래서 여기서 새 트랜잭션이 열리고,
     * 이 작업이 실패해도 이미 저장된 별은 영향을 받지 않는다. 알림은 부가 기능이므로
     * 사용자가 쓴 글을 무산시키면 안 된다.
     *
     * <p>엔티티가 아니라 id를 받는 이유도 같다. 커밋 후에는 호출한 쪽의 엔티티가 영속성
     * 컨텍스트에서 떨어져 있어 지연 로딩이 깨진다. 여기서 다시 읽는다.
     */
    @Transactional
    public void notifySubscribers(Long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return; // 커밋과 이 호출 사이에 삭제되었다면 알릴 것이 없다.
        }

        List<RegionSubscription> matching = subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.covers(event.getLatitude(), event.getLongitude()))
                .filter(subscription -> !subscription.getUser().getId().equals(event.getAuthor().getId()))
                .toList();

        for (RegionSubscription subscription : matching) {
            String message = "[%s] 근처에서 새 이벤트가 등록되었습니다: %s"
                    .formatted(subscription.getLabel(), event.getTitle());
            notificationRepository.save(new Notification(subscription.getUser(), event, message));
        }
    }

    public List<NotificationResponse> findMine(User user) {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDesc(user).stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
