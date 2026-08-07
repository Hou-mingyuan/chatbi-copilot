package com.chatbi.copilot.text2sql.plan;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MySqlQueryPlanAnalyzer implements QueryPlanAnalyzer {
    @Override
    public boolean supports(String dbType) {
        return "mysql".equalsIgnoreCase(dbType);
    }

    @Override
    public PlanEstimate analyze(Connection connection, String sql, int timeoutSeconds) throws java.sql.SQLException {
        long estimatedRows = 0;
        boolean fullScan = false;
        List<String> summary = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
                Map<String, Integer> columns = columns(rs.getMetaData());
                while (rs.next()) {
                    String table = value(rs, columns, "table");
                    String accessType = value(rs, columns, "type");
                    String extra = value(rs, columns, "extra");
                    long rows = longValue(rs, columns, "rows");
                    estimatedRows = saturatingAdd(estimatedRows, rows);
                    fullScan |= "ALL".equalsIgnoreCase(accessType);
                    summary.add("table=" + safe(table) + " access=" + safe(accessType)
                            + " rows=" + rows + " extra=" + safe(extra));
                }
            }
        }
        return new PlanEstimate(estimatedRows, fullScan, List.copyOf(summary));
    }

    private Map<String, Integer> columns(ResultSetMetaData metadata) throws java.sql.SQLException {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            result.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
        }
        return result;
    }

    private String value(ResultSet rs, Map<String, Integer> columns, String name) throws java.sql.SQLException {
        Integer index = columns.get(name);
        return index == null ? "" : rs.getString(index);
    }

    private long longValue(ResultSet rs, Map<String, Integer> columns, String name) throws java.sql.SQLException {
        Integer index = columns.get(name);
        return index == null ? 0 : Math.max(0, rs.getLong(index));
    }

    private long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
