package com.chatbi.copilot.auth.controller;

import com.chatbi.copilot.auth.dto.CurrentUserVo;
import com.chatbi.copilot.auth.dto.LoginRequest;
import com.chatbi.copilot.auth.dto.LoginResponse;
import com.chatbi.copilot.auth.service.AuthService;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUser;
    private final AuthProperties properties;

    public AuthController(AuthService authService, CurrentUserService currentUser, AuthProperties properties) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletRequest servletRequest) {
        AuthService.SessionLogin login = authService.login(
                request.getUsername(), request.getPassword(), servletRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        sessionCookie(login.rawToken(), false).toString(),
                        csrfCookie(login.response().csrfToken(), false).toString())
                .body(ApiResponse.ok(login.response()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserVo> me() {
        return ApiResponse.ok(CurrentUserVo.from(currentUser.required()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout(currentUser.required().sessionId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        sessionCookie("", true).toString(),
                        csrfCookie("", true).toString())
                .body(ApiResponse.ok());
    }

    private ResponseCookie sessionCookie(String value, boolean clear) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(clear ? Duration.ZERO : Duration.ofHours(properties.getSessionHours()))
                .build();
    }

    private ResponseCookie csrfCookie(String value, boolean clear) {
        return ResponseCookie.from(properties.getCsrfCookieName(), value)
                .httpOnly(false)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(clear ? Duration.ZERO : Duration.ofHours(properties.getSessionHours()))
                .build();
    }
}
