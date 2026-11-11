package com.earth.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    FLARE_NOT_FOUND(HttpStatus.NOT_FOUND, "플레어를 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LEVEL_NOT_ENOUGH(HttpStatus.FORBIDDEN, "채팅방 입장에 필요한 레벨이 부족합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    FLARE_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "1시간 안에 등록할 수 있는 플레어 개수를 초과했습니다."),
    CHAT_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도해 주세요."),
    CHAT_DUPLICATE_CONTENT(HttpStatus.TOO_MANY_REQUESTS, "같은 내용을 반복해서 보낼 수 없습니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "올릴 수 없는 내용이 포함되어 있습니다."),
    CANNOT_CONFIRM_OWN_FLARE(HttpStatus.BAD_REQUEST, "자신이 등록한 플레어에는 공감할 수 없습니다."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 공감한 플레어입니다."),
    INVALID_BOUNDS(HttpStatus.BAD_REQUEST, "조회 범위가 올바르지 않습니다."),
    COUNTRY_RESTRICTED(HttpStatus.FORBIDDEN, "로그인하지 않으면 접속한 국가의 플레어만 자세히 볼 수 있습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
