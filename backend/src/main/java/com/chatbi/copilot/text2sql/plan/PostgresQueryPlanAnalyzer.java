package com.chatbi.copilot.text2sql.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Component
public class PostgresQueryPlanAnalyzer implements QueryPlanAnalyzer {
    private final ObjectMapper objectMapper;

    public PostgresQueryPlanAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String dbType) {
        return dbType != null && dbType.toLowerCase().startsWith("postg");
    }

    @Override
    public PlanEstimate analyze(Connection connection, String sql, int timeoutSeconds) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet rs = statement.executeQuery("EXPLAIN (FORMAT JSON) " + sql)) {
                if (!rs.next()) {
                    return new PlanEstimate(0, false, List.of("empty plan"));
                }
                JsonNode root = objectMapper.readTree(rs.getString(1)).path(0).path("Plan");
                PlanAccumulator accumulator = new PlanAccumulator();
                walk(root, accumulator);
                return new PlanEstimate(accumulator.estimatedRows, accumulator.fullScan,
                        List.copyOf(accumulator.summary));
            } catch (java.io.IOException e) {
                throw new java.sql.SQLException("PostgreSQL EXPLAIN JSON could not be parsed", e);
            }
        }
    }

    private void walk(JsonNode node, PlanAccumulator accumulator) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        String type = node.path("Node Type").asText("Unknown");
        String relation = node.path("Relation Name").asText("");
        long rows = Math.max(0, node.path("Plan Rows").asLong(0));
        accumulator.estimatedRows = Math.max(accumulator.estimatedRows, rows);
        accumulator.fullScan |= "Seq Scan".equals(type);
        accumulator.summary.add("node=" + type + (relation.isBlank() ? "" : " table=" + relation)
                + " rows=" + rows);
        node.path("Plans").forEach(child -> walk(child, accumulator));
    }

    private static class PlanAccumulator {
        private long estimatedRows;
        private boolean fullScan;
        private final List<String> summary = new ArrayList<>();
    }
}
