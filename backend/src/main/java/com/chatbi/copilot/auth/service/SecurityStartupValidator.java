package com.chatbi.copilot.auth.service;

import com.chatbi.copilot.config.AuthProperties;
import com.chatbi.copilot.config.SecurityProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityStartupValidator implements ApplicationRunner {
    private static final String DEV_SECRET = "chatbi-copilot-dev-secret-change-me";

    private final AuthProperties auth;
    private final SecurityProperties security;

    public SecurityStartupValidator(AuthProperties auth, SecurityProperties security) {
        this.auth = auth;
        this.security = security;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!auth.isProductionMode()) {
            return;
        }
        if (security.getSecret() == null || security.getSecret().isBlank()
                || DEV_SECRET.equals(security.getSecret())) {
            throw new IllegalStateException("CHATBI_SECRET must be changed in production mode");
        }
        if (auth.isDemoAuthEnabled()) {
            throw new IllegalStateException("DEMO_AUTH_ENABLED must be false in production mode");
        }
        if (!auth.isCookieSecure()) {
            throw new IllegalStateException("CHATBI_COOKIE_SECURE must be true in production mode");
        }
        if (auth.getAllowedOrigins().stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("Wildcard CORS origins are forbidden in production mode");
        }
    }
}
