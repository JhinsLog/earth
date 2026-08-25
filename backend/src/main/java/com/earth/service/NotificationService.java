package com.earth.service;

import com.earth.domain.event.Event;
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

    public NotificationService(RegionSubscriptionRepository subscriptionRepository,
                                NotificationRepository notificationRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationRepository = notificationRepository;
    }

    /** 새 이벤트 위치를 커버하는 구독을 가진 사용자들에게 알림 레코드를 남긴다. */
    @Transactional
    public void notifySubscribers(Event event) {
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
