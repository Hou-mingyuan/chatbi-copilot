package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chatbi.sql-guard")
public class SqlGuardProperties {

    /** LIMIT injected when a generated query has none. */
    private int defaultLimit = 500;

    /** Hard cap; any larger LIMIT is clamped down to this value. */
    private int maxLimit = 5000;
}
