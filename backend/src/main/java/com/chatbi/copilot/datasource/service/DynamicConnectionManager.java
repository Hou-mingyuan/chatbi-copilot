package com.chatbi.copilot.datasource.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.dto.ConnectionTestResult;
import com.chatbi.copilot.datasource.service.readonly.ReadOnlyAccountVerifier;
import com.chatbi.copilot.datasource.service.readonly.ReadOnlyVerification;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages read-only connection pools to the user's <b>target</b> databases (one pool per datasource).
 * Pools are created lazily and cached; they are evicted when the config changes or is deleted.
 */
@Slf4j
@Component
public class DynamicConnectionManager {

    private final PasswordCipher cipher;
    private final JdbcParameterPolicy jdbcParameterPolicy;
    private final List<ReadOnlyAccountVerifier> readOnlyVerifiers;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DynamicConnectionManager(PasswordCipher cipher, JdbcParameterPolicy jdbcParameterPolicy,
                                    List<ReadOnlyAccountVerifier> readOnlyVerifiers) {
        this.cipher = cipher;
        this.jdbcParameterPolicy = jdbcParameterPolicy;
        this.readOnlyVerifiers = readOnlyVerifiers;
    }

    public DataSource getPool(DataSourceConfig config) {
        if (!Integer.valueOf(1).equals(config.getVerifiedReadOnly())) {
            throw new BusinessException("Datasource must pass read-only account verification before use");
        }
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
        hc.setConnectionInitSql(connectionInitSql(config.getDbType()));
        hc.setPoolName("target-ds-" + config.getId());
        log.info("Creating connection pool for datasource id={} ({})", config.getId(), config.getName());
        return new HikariDataSource(hc);
    }

    /** Validate a connection using a throwaway (non-pooled) connection. */
    public ConnectionTestResult testConnection(DataSourceConfig config) {
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
            conn.setReadOnly(true);
            ReadOnlyAccountVerifier verifier = readOnlyVerifiers.stream()
                    .filter(candidate -> candidate.supports(config.getDbType()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("No read-only verifier for " + config.getDbType()));
            ReadOnlyVerification verification = verifier.verify(conn);
            if (!verification.readOnly()) {
                throw new BusinessException("Database account has write-capable privileges; use a SELECT-only account");
            }
            return new ConnectionTestResult(true, true,
                    conn.getMetaData().getDatabaseProductName(),
                    conn.getMetaData().getDatabaseProductVersion(), verification.evidence());
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            log.warn("Datasource connection verification failed: {}", e.getMessage());
            throw new BusinessException("Connection could not be verified");
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
        String params = jdbcParameterPolicy.sanitize(c.getDbType(), c.getJdbcParams());
        boolean hasParams = params != null && !params.isBlank();
        switch (type) {
            case "mysql" -> {
                String base = "jdbc:mysql://" + c.getHost() + ":" + c.getPort() + "/" + c.getDatabaseName();
                // useInformationSchema + remarks make DatabaseMetaData return table/column comments
                String def = "sslMode=PREFERRED&serverTimezone=UTC&useInformationSchema=true&remarks=true"
                        + "&useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull"
                        + "&allowPublicKeyRetrieval=true";
                return base + "?" + def + (hasParams ? "&" + params : "");
            }
            case "postgresql", "postgres" -> {
                String base = "jdbc:postgresql://" + c.getHost() + ":" + c.getPort() + "/" + c.getDatabaseName();
                return base + (hasParams ? "?" + params : "");
            }
            default -> throw new BusinessException("Unsupported dbType: " + c.getDbType());
        }
    }

    private String connectionInitSql(String dbType) {
        return dbType != null && dbType.toLowerCase().startsWith("postg")
                ? "SET default_transaction_read_only = on"
                : "SET SESSION TRANSACTION READ ONLY";
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
