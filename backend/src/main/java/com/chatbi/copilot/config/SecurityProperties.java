package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chatbi.security")
public class SecurityProperties {

    /** Secret used to derive the AES key that encrypts stored datasource passwords. */
    private String secret = "chatbi-copilot-dev-secret-change-me";
}
