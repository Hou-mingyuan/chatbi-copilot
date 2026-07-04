package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DynamicConnectionManager;
import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
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

    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final DynamicConnectionManager connectionManager;

    public SqlExecutor(DynamicConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public QueryExecResult execute(DataSourceConfig config, String sql, int maxRows) {
        DataSource ds = connectionManager.getPool(config);
        QueryExecResult result = new QueryExecResult();
        long start = System.currentTimeMillis();

        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (Statement st = conn.createStatement()) {
                st.setMaxRows(maxRows);
                st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
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
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(names.get(i - 1), normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    result.setRows(rows);
                    result.setRowCount(rows.size());
                    result.setTruncated(rows.size() >= maxRows);
                }
            }
        } catch (SQLException e) {
            log.warn("SQL execution failed: {}", e.getMessage());
            throw new BusinessException("SQL execution failed: " + e.getMessage());
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
        return v;
    }
}
