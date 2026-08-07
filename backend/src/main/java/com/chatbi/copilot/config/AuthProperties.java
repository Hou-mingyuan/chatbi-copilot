package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "chatbi.security")
public class AuthProperties {

    private boolean productionMode;
    private boolean cookieSecure;
    private int sessionHours = 8;
    private String cookieName = "CHATBI_SESSION";
    private String csrfCookieName = "CHATBI_CSRF";
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:19031", "http://127.0.0.1:19031"));
    private boolean demoAuthEnabled = true;
    private String demoAdminPassword = "ChatBI!Admin123";
    private String demoAnalystPassword = "ChatBI!Analyst123";
    private String demoViewerPassword = "ChatBI!Viewer123";
}
