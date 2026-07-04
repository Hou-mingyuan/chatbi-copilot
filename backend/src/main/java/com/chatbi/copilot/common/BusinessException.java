package com.chatbi.copilot.common;

import lombok.Getter;

/**
 * Domain-level exception carrying a business error code. Mapped to a clean JSON response
 * by {@link GlobalExceptionHandler}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }
}
