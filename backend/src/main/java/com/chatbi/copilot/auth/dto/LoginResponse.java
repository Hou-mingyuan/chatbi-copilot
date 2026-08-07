package com.chatbi.copilot.auth.dto;

public record LoginResponse(CurrentUserVo user, String csrfToken, long expiresInSeconds) {
}
