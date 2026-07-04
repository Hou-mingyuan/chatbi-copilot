package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional demo datasource auto-registered on startup for a one-click experience.
 */
@Data
@ConfigurationProperties(prefix = "chatbi.demo-datasource")
public class DemoDataSourceProperties {

    private boolean enabled = false;
    private String name = "Demo - Sales (MySQL)";
    private String dbType = "mysql";
    private String host = "localhost";
    private int port = 3306;
    private String databaseName = "chatbi_demo";
    private String username = "chatbi";
    private String password = "chatbi123";
}
