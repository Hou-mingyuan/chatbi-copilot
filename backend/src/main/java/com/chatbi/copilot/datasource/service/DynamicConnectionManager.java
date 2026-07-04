package com.chatbi.copilot.datasource.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages read-only connection pools to the user's <b>target</b> databases (one pool per datasource).
 * Pools are created lazily and cached; they are evicted when the config changes or is deleted.
 */
@Slf4j
@Component
public class DynamicConnectionManager {

    private final PasswordCipher cipher;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DynamicConnectionManager(PasswordCipher cipher) {
        this.cipher = cipher;
    }

    public DataSource getPool(DataSourceConfig config) {
        return pools.computeIfAbsent(config.getId(), id -> buildPool(config));
    }

    private HikariDataSource buildPool(DataSourceConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(buildJdbcUrl(config));
        hc.setUsername(config.getUsername());
        hc.setPassword(cipher.decrypt(config.getPassword()));
        hc.setDriverClassName(driverClass(config.getDbType()));
        hc.setMaximumPoolSize(5);
        hc.setMinimumIdle(0);
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(60_000);
        hc.setMaxLifetime(600_000);
        hc.setReadOnly(true); // defense in depth on top of the SQL guard
        hc.setPoolName("target-ds-" + config.getId());
        log.info("Creating connection pool for datasource id={} ({})", config.getId(), config.getName());
        return new HikariDataSource(hc);
    }

    /** Validate a connection using a throwaway (non-pooled) connection. */
    public void testConnection(DataSourceConfig config) {
        String url = buildJdbcUrl(config);
        String password = cipher.decrypt(config.getPassword());
        try {
            Class.forName(driverClass(config.getDbType()));
        } catch (ClassNotFoundException e) {
            throw new BusinessException("JDBC driver not found: " + e.getMessage());
        }
        try (Connection conn = DriverManager.getConnection(url, config.getUsername(), password)) {
            if (!conn.isValid(5)) {
                throw new BusinessException("Connection is not valid");
            }
        } catch (Exception e) {
            throw new BusinessException("Connection failed: " + e.getMessage());
        }
    }

    public void evict(Long id) {
        HikariDataSource ds = pools.remove(id);
        if (ds != null) {
            ds.close();
            log.info("Evicted connection pool for datasource id={}", id);
        }
    }

    public String buildJdbcUrl(DataSourceConfig c) {
        String type = c.getDbType() == null ? "" : c.getDbType().toLowerCase();
        String params = c.getJdbcParams();
        boolean hasParams = params != null && !params.isBlank();
        switch (type) {
            case "mysql" -> {
                String base = "jdbc:mysql://" + c.getHost() + ":" + c.getPort() + "/" + c.getDatabaseName();
                // useInformationSchema + remarks make DatabaseMetaData return table/column comments
                String def = "useSSL=false&serverTimezone=UTC&useInformationSchema=true&remarks=true"
                        + "&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true";
                return base + "?" + def + (hasParams ? "&" + params : "");
            }
            case "postgresql", "postgres" -> {
                String base = "jdbc:postgresql://" + c.getHost() + ":" + c.getPort() + "/" + c.getDatabaseName();
                return base + (hasParams ? "?" + params : "");
            }
            default -> throw new BusinessException("Unsupported dbType: " + c.getDbType());
        }
    }

    private String driverClass(String dbType) {
        return switch (dbType == null ? "" : dbType.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql", "postgres" -> "org.postgresql.Driver";
            default -> throw new BusinessException("Unsupported dbType: " + dbType);
        };
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
