package com.earth.dto;

import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareOrigin;
import com.earth.domain.flare.FlareStatus;

import java.time.Instant;

public record FlareResponse(
        Long id,
        String title,
        String content,
        FlareCategory category,
        FlareStatus status,
        double latitude,
        double longitude,
        Long authorId,
        String authorNickname,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        /** "나도 봤다"고 확인한 사람 수. 플레어의 크기·밝기와 수명에 반영된다. */
        int confirmCount,
        /**
         * 지금 보고 있는 사용자가 이미 공감했는지.
         *
         * <p>비로그인이거나 실시간 전파처럼 특정 사용자를 알 수 없는 경로에서는 false다.
         * 화면은 이 값으로 공감 버튼의 눌림 상태를 정한다.
         */
        boolean confirmedByMe,
        /** 플레어가 속한 국가(ISO 3166-1 alpha-2). 공해 등은 null. */
        String countryCode,
        /**
         * 위치와 존재만 담긴 응답인지. 비로그인 사용자가 접속 국가 밖의 플레어를 볼 때 true이고, 이때 제목·내용·
         * 분류·작성자·공감 수는 비어 있다. 화면은 이 플레어를 중립색으로 그리고 상세를 열지 않는다.
         */
        boolean restricted,
        /** 플레어를 만든 주체(사용자·외부 데이터). 제한된 응답에서는 null. */
        FlareOrigin origin
) {
    /** 실시간 전파처럼 "누가 보는지" 알 수 없는 자리에서 쓴다. */
    public static FlareResponse from(Flare flare) {
        return from(flare, false);
    }

    public static FlareResponse from(Flare flare, boolean confirmedByMe) {
        return new FlareResponse(
                flare.getId(),
                flare.getTitle(),
                flare.getContent(),
                flare.getCategory(),
                flare.getStatus(),
                flare.getLatitude(),
                flare.getLongitude(),
                flare.getAuthor().getId(),
                flare.getAuthor().getNickname(),
                flare.getCreatedAt(),
                flare.getUpdatedAt(),
                flare.getExpiresAt(),
                flare.getConfirmCount(),
                confirmedByMe,
                flare.getCountryCode(),
                false,
                flare.getOrigin()
        );
    }

    /**
     * 위치와 존재만 남긴다. 만료 시각은 남긴다 — 화면이 수명이 다한 플레어를 스스로 걷어내는 데 필요하다.
     * 생성 시각도 지운다. 최신순 정렬로 대략의 등록 시각은 드러나지만, 정확한 시각까지 줄 이유는 없다.
     */
    public FlareResponse toRestricted() {
        return new FlareResponse(id, null, null, null, status, latitude, longitude, null, null,
                null, null, expiresAt, 0, false, countryCode, true, null);
    }
}
