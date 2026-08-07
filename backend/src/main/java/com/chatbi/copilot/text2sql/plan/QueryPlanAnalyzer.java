package com.chatbi.copilot.text2sql.plan;

import java.sql.Connection;
import java.sql.SQLException;

public interface QueryPlanAnalyzer {
    boolean supports(String dbType);

    PlanEstimate analyze(Connection connection, String sql, int timeoutSeconds) throws SQLException;
}
