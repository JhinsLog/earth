package com.earth.dto;

import com.earth.domain.notification.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id, Long eventId, String message, boolean read, Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getEvent().getId(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
