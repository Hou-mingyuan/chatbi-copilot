package com.chatbi.copilot.config;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup gate mirroring the ai-service-agent ProductionGuard pattern: whenever
 * {@code chatbi.security.production-mode} is on, refuse to boot a configuration that
 * would ship demo credentials, default secrets or a mock model to real users.
 */
@Component
public class ProductionGuard {

    private static final String DEFAULT_SECURITY_SECRET = "chatbi-copilot-dev-secret-change-me";
    private static final int MIN_SECRET_LENGTH = 32;

    private final AuthProperties auth;
    private final SecurityProperties security;
    private final LlmProperties llm;
    private final DemoDataSourceProperties demoDataSource;

    public ProductionGuard(
            AuthProperties auth,
            SecurityProperties security,
            LlmProperties llm,
            DemoDataSourceProperties demoDataSource) {
        this.auth = auth;
        this.security = security;
        this.llm = llm;
        this.demoDataSource = demoDataSource;
        guard();
    }

    void guard() {
        if (!auth.isProductionMode()) {
            return;
        }
        if (auth.isDemoAuthEnabled()) {
            throw new IllegalStateException(
                    "Production mode requires chatbi.security.demo-auth-enabled=false; demo accounts must not exist for real users");
        }
        String secret = security.getSecret();
        if (secret == null || secret.isBlank() || DEFAULT_SECURITY_SECRET.equals(secret) || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Production mode requires chatbi.security.secret to be a unique value of at least " + MIN_SECRET_LENGTH
                            + " characters; it derives the AES key that encrypts stored datasource passwords");
        }
        if (!auth.isCookieSecure()) {
            throw new IllegalStateException("Production mode requires chatbi.security.cookie-secure=true");
        }
        List<String> origins = auth.getAllowedOrigins();
        if (origins == null || origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException(
                    "Production mode requires chatbi.security.allowed-origins to enumerate the real frontend origins");
        }
        if ("mock".equalsIgnoreCase(llm.getProvider())) {
            throw new IllegalStateException("Production mode refuses the mock LLM provider; configure a real OpenAI-compatible endpoint");
        }
        if (llm.getApiKey() == null || llm.getApiKey().isBlank()) {
            throw new IllegalStateException("Production mode requires chatbi.llm.api-key to be set");
        }
        if (demoDataSource.isEnabled()) {
            throw new IllegalStateException(
                    "Production mode requires chatbi.demo-datasource.enabled=false; register the real datasources instead");
        }
    }
}
