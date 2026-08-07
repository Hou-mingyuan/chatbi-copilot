package com.chatbi.copilot.auth.security;

import com.chatbi.copilot.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

final class JsonSecurityResponse {
    private JsonSecurityResponse() {
    }

    static void write(HttpServletResponse response, ObjectMapper mapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
    }
}
