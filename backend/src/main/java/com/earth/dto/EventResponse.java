package com.earth.dto;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventStatus;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String content,
        EventCategory category,
        EventStatus status,
        double latitude,
        double longitude,
        Long authorId,
        String authorNickname,
        Instant createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getContent(),
                event.getCategory(),
                event.getStatus(),
                event.getLatitude(),
                event.getLongitude(),
                event.getAuthor().getId(),
                event.getAuthor().getNickname(),
                event.getCreatedAt()
        );
    }
}
