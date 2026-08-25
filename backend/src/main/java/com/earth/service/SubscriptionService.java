package com.earth.service;

import com.earth.domain.subscription.RegionSubscription;
import com.earth.domain.subscription.RegionSubscriptionRepository;
import com.earth.domain.user.User;
import com.earth.dto.SubscriptionRequest;
import com.earth.dto.SubscriptionResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private final RegionSubscriptionRepository subscriptionRepository;

    public SubscriptionService(RegionSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public List<SubscriptionResponse> findMine(User user) {
        return subscriptionRepository.findAllByUser(user).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionResponse create(User user, SubscriptionRequest request) {
        RegionSubscription subscription = new RegionSubscription(
                user, request.label(), request.latitude(), request.longitude(), request.radiusKm());
        subscriptionRepository.save(subscription);
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public void delete(User user, Long subscriptionId) {
        RegionSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (!subscription.getUser().getId().equals(user.getId())) {
            throw new EarthApiException(ErrorCode.FORBIDDEN);
        }
        subscriptionRepository.delete(subscription);
    }
}
