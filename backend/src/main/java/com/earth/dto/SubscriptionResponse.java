package com.earth.dto;

import com.earth.domain.subscription.RegionSubscription;

public record SubscriptionResponse(
        Long id, String label, double latitude, double longitude, double radiusKm
) {
    public static SubscriptionResponse from(RegionSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(), subscription.getLabel(),
                subscription.getLatitude(), subscription.getLongitude(), subscription.getRadiusKm());
    }
}
