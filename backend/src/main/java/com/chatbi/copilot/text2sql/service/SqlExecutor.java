package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DynamicConnectionManager;
import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import com.chatbi.copilot.config.SqlGuardProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLTimeoutException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a sanitized read-only query against a target datasource with row and time caps.
 */
@Slf4j
@Component
public class SqlExecutor {

    private static final long MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L;

    private final DynamicConnectionManager connectionManager;
    private final QueryCancellationRegistry cancellationRegistry;
    private final SqlGuardProperties properties;

    public SqlExecutor(DynamicConnectionManager connectionManager,
                       QueryCancellationRegistry cancellationRegistry,
                       SqlGuardProperties properties) {
        this.connectionManager = connectionManager;
        this.cancellationRegistry = cancellationRegistry;
        this.properties = properties;
    }

    public QueryExecResult execute(DataSourceConfig config, String sql, int maxRows) {
        return execute(config, sql, maxRows, null);
    }

    public QueryExecResult execute(DataSourceConfig config, String sql, int maxRows, String executionId) {
        DataSource ds = connectionManager.getPool(config);
        QueryExecResult result = new QueryExecResult();
        long start = System.currentTimeMillis();

        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (Statement st = conn.createStatement()) {
                st.setMaxRows(maxRows + 1);
                st.setQueryTimeout(properties.getQueryTimeoutSeconds());
                cancellationRegistry.register(executionId, st);
                try (ResultSet rs = st.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    List<String> names = new ArrayList<>();
                    List<ColumnMeta> columns = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        String label = md.getColumnLabel(i);
                        if (label == null || label.isBlank()) {
                            label = md.getColumnName(i);
                        }
                        label = dedupe(names, label);
                        names.add(label);
                        columns.add(new ColumnMeta(label, md.getColumnTypeName(i), null));
                    }
                    result.setColumns(columns);

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next() && rows.size() <= maxRows) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(names.get(i - 1), normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    boolean truncated = rows.size() > maxRows;
                    if (truncated) {
                        rows.remove(rows.size() - 1);
                    }
                    result.setRows(rows);
                    result.setRowCount(rows.size());
                    result.setTruncated(truncated);
                } finally {
                    cancellationRegistry.unregister(executionId, st);
                }
            }
        } catch (SQLTimeoutException e) {
            log.warn("SQL execution timed out state={} code={}", e.getSQLState(), e.getErrorCode());
            throw new BusinessException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                    "Query exceeded the execution timeout");
        } catch (SQLException e) {
            log.warn("SQL execution failed state={} code={} message={}",
                    e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw new BusinessException("Query could not be executed. Check columns, filters, and permissions.");
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    private String dedupe(List<String> existing, String name) {
        if (!existing.contains(name)) {
            return name;
        }
        int i = 2;
        while (existing.contains(name + "_" + i)) {
            i++;
        }
        return name + "_" + i;
    }

    private Object normalize(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (v instanceof byte[]) {
            return "[binary]";
        }
        if (v instanceof BigDecimal decimal && decimal.precision() > 15) {
            return decimal.toPlainString();
        }
        if (v instanceof BigInteger integer && integer.abs().toString().length() > 15) {
            return integer.toString();
        }
        if (v instanceof Long number && (number > MAX_SAFE_JSON_INTEGER || number < -MAX_SAFE_JSON_INTEGER)) {
            return number.toString();
        }
        return v;
    }
}
