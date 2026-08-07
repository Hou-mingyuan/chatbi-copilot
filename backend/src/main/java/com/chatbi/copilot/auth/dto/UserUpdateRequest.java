package com.chatbi.copilot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    @NotBlank(message = "displayName is required")
    @Size(max = 120, message = "displayName is too long")
    private String displayName;
    private boolean enabled = true;
    @NotEmpty(message = "at least one role is required")
    private Set<String> roles;
    @Size(max = 200, message = "password is too long")
    private String password;
}
