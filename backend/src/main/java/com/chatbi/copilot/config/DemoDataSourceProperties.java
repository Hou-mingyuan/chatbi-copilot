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
    private String username = "chatbi_ro";
    private String password = "ChatBI!Readonly123";
    private boolean postgresEnabled;
    private String postgresName = "Demo - Sales (PostgreSQL)";
    private String postgresHost = "localhost";
    private int postgresPort = 5432;
    private String postgresDatabaseName = "chatbi_demo";
    private String postgresUsername = "chatbi_ro";
    private String postgresPassword = "ChatBI!Readonly123";
}
