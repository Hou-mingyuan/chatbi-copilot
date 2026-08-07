package com.chatbi.copilot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreateRequest {
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 80, message = "username must contain 3-80 characters")
    private String username;
    @NotBlank(message = "displayName is required")
    @Size(max = 120, message = "displayName is too long")
    private String displayName;
    @NotBlank(message = "password is required")
    @Size(min = 12, max = 200, message = "password must contain 12-200 characters")
    private String password;
    @NotEmpty(message = "at least one role is required")
    private Set<String> roles;
}
