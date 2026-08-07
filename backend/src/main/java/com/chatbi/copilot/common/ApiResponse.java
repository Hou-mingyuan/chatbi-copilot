package com.chatbi.copilot.common;

import lombok.Data;
import org.slf4j.MDC;

/**
 * Uniform API envelope. {@code code == 0} means success.
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = MDC.get(RequestIdFilter.MDC_KEY);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
