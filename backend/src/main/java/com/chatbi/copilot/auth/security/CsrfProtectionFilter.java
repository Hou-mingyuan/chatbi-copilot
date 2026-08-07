package com.chatbi.copilot.auth.security;

import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.TokenHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final ObjectMapper objectMapper;

    public CsrfProtectionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || request.getRequestURI().endsWith("/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            String supplied = request.getHeader("X-CSRF-Token");
            if (!TokenHasher.matches(supplied, principal.csrfHash())) {
                JsonSecurityResponse.write(response, objectMapper, HttpStatus.FORBIDDEN.value(),
                        "Invalid or missing CSRF token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
