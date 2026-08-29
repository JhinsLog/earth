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
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        /** "나도 봤다"고 확인한 사람 수. 별의 크기·밝기와 수명에 반영된다. */
        int confirmCount,
        /**
         * 지금 보고 있는 사용자가 이미 공감했는지.
         *
         * <p>비로그인이거나 실시간 전파처럼 특정 사용자를 알 수 없는 경로에서는 false다.
         * 화면은 이 값으로 공감 버튼의 눌림 상태를 정한다.
         */
        boolean confirmedByMe
) {
    /** 실시간 전파처럼 "누가 보는지" 알 수 없는 자리에서 쓴다. */
    public static EventResponse from(Event event) {
        return from(event, false);
    }

    public static EventResponse from(Event event, boolean confirmedByMe) {
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
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getExpiresAt(),
                event.getConfirmCount(),
                confirmedByMe
        );
    }
}
