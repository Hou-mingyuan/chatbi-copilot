package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chatbi.query-jobs")
public class QueryJobProperties {
    private int totalTimeoutSeconds = 90;
    private int maxActivePerUser = 3;
    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 50;
}
