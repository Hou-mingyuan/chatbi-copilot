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

    /** Reject offsets above this value to avoid intentionally expensive deep scans. */
    private long maxOffset = 100000;

    private int queryTimeoutSeconds = 15;
    private long maxEstimatedRows = 100000;
    private long confirmEstimatedRows = 10000;
}
