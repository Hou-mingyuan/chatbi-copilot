package com.chatbi.copilot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "username is required")
    @Size(max = 80, message = "username is too long")
    private String username;
    @NotBlank(message = "password is required")
    @Size(max = 200, message = "password is too long")
    private String password;
}
