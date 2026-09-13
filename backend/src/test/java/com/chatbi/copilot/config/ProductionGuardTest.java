package com.chatbi.copilot.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionGuardTest {

    private AuthProperties productionAuth() {
        AuthProperties auth = new AuthProperties();
        auth.setProductionMode(true);
        auth.setDemoAuthEnabled(false);
        auth.setCookieSecure(true);
        auth.setAllowedOrigins(List.of("https://bi.example.com"));
        return auth;
    }

    private SecurityProperties productionSecurity() {
        SecurityProperties security = new SecurityProperties();
        security.setSecret("a-unique-production-secret-with-32-characters");
        return security;
    }

    private LlmProperties productionLlm() {
        LlmProperties llm = new LlmProperties();
        llm.setProvider("deepseek");
        llm.setApiKey("sk-real-key");
        return llm;
    }

    @Test
    void developmentDefaultsStayBooted() {
        assertDoesNotThrow(() -> new ProductionGuard(
                new AuthProperties(), new SecurityProperties(), new LlmProperties(), new DemoDataSourceProperties()));
    }

    @Test
    void acceptsFullySecuredProductionConfiguration() {
        DemoDataSourceProperties demo = new DemoDataSourceProperties();
        demo.setEnabled(false);
        assertDoesNotThrow(() -> new ProductionGuard(
                productionAuth(), productionSecurity(), productionLlm(), demo));
    }

    @Test
    void rejectsEachUnsafeProductionTransition() {
        DemoDataSourceProperties demoOff = new DemoDataSourceProperties();
        demoOff.setEnabled(false);

        AuthProperties demoAuth = productionAuth();
        demoAuth.setDemoAuthEnabled(true);
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                demoAuth, productionSecurity(), productionLlm(), demoOff));

        SecurityProperties defaultSecret = new SecurityProperties();
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                productionAuth(), defaultSecret, productionLlm(), demoOff));

        SecurityProperties shortSecret = new SecurityProperties();
        shortSecret.setSecret("short");
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                productionAuth(), shortSecret, productionLlm(), demoOff));

        AuthProperties insecureCookie = productionAuth();
        insecureCookie.setCookieSecure(false);
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                insecureCookie, productionSecurity(), productionLlm(), demoOff));

        AuthProperties wildcardCors = productionAuth();
        wildcardCors.setAllowedOrigins(List.of("*"));
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                wildcardCors, productionSecurity(), productionLlm(), demoOff));

        LlmProperties mockLlm = productionLlm();
        mockLlm.setProvider("mock");
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                productionAuth(), productionSecurity(), mockLlm, demoOff));

        DemoDataSourceProperties demoOn = new DemoDataSourceProperties();
        demoOn.setEnabled(true);
        assertThrows(IllegalStateException.class, () -> new ProductionGuard(
                productionAuth(), productionSecurity(), productionLlm(), demoOn));
    }
}
