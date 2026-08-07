package com.chatbi.copilot.text2sql.plan;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DynamicConnectionManager;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

@Service
public class QueryPlanService {
    private final DynamicConnectionManager connectionManager;
    private final List<QueryPlanAnalyzer> analyzers;
    private final SqlGuardProperties properties;

    public QueryPlanService(DynamicConnectionManager connectionManager, List<QueryPlanAnalyzer> analyzers,
                            SqlGuardProperties properties) {
        this.connectionManager = connectionManager;
        this.analyzers = analyzers;
        this.properties = properties;
    }

    public QueryRisk analyze(DataSourceConfig config, String sql) {
        QueryPlanAnalyzer analyzer = analyzers.stream().filter(candidate -> candidate.supports(config.getDbType()))
                .findFirst().orElseThrow(() -> new BusinessException("No query-plan analyzer for datasource dialect"));
        try (Connection connection = connectionManager.getPool(config).getConnection()) {
            connection.setReadOnly(true);
            PlanEstimate estimate = analyzer.analyze(connection, sql, properties.getQueryTimeoutSeconds());
            List<String> reasons = new ArrayList<>();
            boolean blocked = estimate.estimatedRows() > properties.getMaxEstimatedRows();
            boolean confirmation = !blocked && (estimate.estimatedRows() > properties.getConfirmEstimatedRows()
                    || (estimate.fullScan() && estimate.estimatedRows() > 1000));
            if (blocked) reasons.add("Estimated scanned rows exceed the hard safety limit");
            if (estimate.fullScan()) reasons.add("Execution plan contains a sequential/full table scan");
            if (confirmation) reasons.add("Estimated scan requires explicit confirmation");
            if (reasons.isEmpty()) reasons.add("Estimated scan is within the configured budget");
            String level = blocked ? "HIGH" : confirmation ? "MEDIUM" : "LOW";
            return new QueryRisk(level, estimate.estimatedRows(), estimate.fullScan(), confirmation,
                    blocked, List.copyOf(reasons), estimate.summary());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Query plan could not be analyzed; execution was denied");
        }
    }
}
