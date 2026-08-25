package com.earth.exception;

import lombok.Getter;

@Getter
public class EarthApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public EarthApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public EarthApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
