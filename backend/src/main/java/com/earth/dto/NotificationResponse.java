package com.earth.dto;

import com.earth.domain.notification.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id, Long flareId, String message, boolean read, Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getFlare().getId(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
