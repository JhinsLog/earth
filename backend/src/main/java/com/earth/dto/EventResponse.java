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
        boolean confirmedByMe,
        /** 별이 속한 국가(ISO 3166-1 alpha-2). 공해 등은 null. */
        String countryCode,
        /**
         * 위치와 존재만 담긴 응답인지. 비로그인 사용자가 접속 국가 밖의 별을 볼 때 true이고, 이때 제목·내용·
         * 분류·작성자·공감 수는 비어 있다. 화면은 이 별을 중립색으로 그리고 상세를 열지 않는다.
         */
        boolean restricted
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
                confirmedByMe,
                event.getCountryCode(),
                false
        );
    }

    /**
     * 위치와 존재만 남긴다. 만료 시각은 남긴다 — 화면이 수명이 다한 별을 스스로 걷어내는 데 필요하다.
     * 생성 시각도 지운다. 최신순 정렬로 대략의 등록 시각은 드러나지만, 정확한 시각까지 줄 이유는 없다.
     */
    public EventResponse toRestricted() {
        return new EventResponse(id, null, null, null, status, latitude, longitude, null, null,
                null, null, expiresAt, 0, false, countryCode, true);
    }
}
