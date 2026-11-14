package com.earth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 브라우저가 모아 보내는 오류·행동 로그 한 묶음.
 *
 * <p>모든 필드에 길이 상한을 둔다. 인증 없이 열린 API라, 상한이 없으면 큰 문자열 몇 개로 로그 저장소(Loki)의 디스크를
 * 채울 수 있다. 이름(name)과 data의 키는 영문 소문자·숫자·점 등으로만 받는다 — 대시보드에서 묶어 세는 기준이라
 * 값이 제멋대로면 집계가 무의미해지고, 줄바꿈 같은 제어 문자가 섞일 여지도 없앤다.
 *
 * @param sessionId 탭 하나를 구분하는 무작위 값(브라우저가 만든다). 같은 사람의 행동을 순서대로 묶어 보는 데 쓴다.
 *                  계정·기기와 연결되는 값이 아니다.
 */
public record ClientEventBatch(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") String sessionId,
        @Valid ClientInfo client,
        @NotEmpty @Size(max = 30) List<@Valid @NotNull ClientEvent> events) {

    /** 기기 정보. 같은 오류가 특정 기기·GPU에서만 나는지 가려내는 데 쓴다. */
    public record ClientInfo(
            @Size(max = 300) String userAgent,
            @Size(max = 200) String gpu,
            @Size(max = 32) String viewport,
            @Positive Double pixelRatio,
            @Size(max = 35) String language) {
    }

    public enum Kind { ERROR, ACTION }

    /**
     * @param at   브라우저 시각(epoch ms). 기기 시계가 틀릴 수 있어 참고용이고, 정렬은 서버 수신 시각으로 한다.
     * @param path 화면 경로(쿼리 제외). 브라우저가 잘라 보내지만 서버에서도 길이를 막는다.
     */
    public record ClientEvent(
            @NotNull Kind kind,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9._-]+") String name,
            @Size(max = 1000) String message,
            @Size(max = 4000) String stack,
            @Size(max = 200) String path,
            @NotNull @Positive Long at,
            @Size(max = 10) Map<@Size(max = 32) @Pattern(regexp = "[a-zA-Z0-9_]+") String, @Size(max = 200) String> data) {
    }
}
