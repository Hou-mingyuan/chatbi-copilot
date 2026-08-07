package com.chatbi.copilot.auth.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserVo(Long id, String username, String displayName, boolean enabled,
                     Set<String> roles, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
