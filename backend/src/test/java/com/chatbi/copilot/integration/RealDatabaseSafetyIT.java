package com.chatbi.copilot.integration;

import com.chatbi.copilot.chart.ChartRecommender;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ConnectionTestResult;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DynamicConnectionManager;
import com.chatbi.copilot.datasource.service.SchemaInspector;
import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import com.chatbi.copilot.text2sql.plan.QueryPlanService;
import com.chatbi.copilot.text2sql.plan.QueryRisk;
import com.chatbi.copilot.text2sql.service.ResultInterpreter;
import com.chatbi.copilot.text2sql.service.SqlExecutor;
import com.chatbi.copilot.text2sql.service.SqlGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:real-database-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=false",
        "server.port=0"
})
class RealDatabaseSafetyIT {
    @Autowired private DynamicConnectionManager connectionManager;
    @Autowired private SchemaInspector schemaInspector;
    @Autowired private SqlGuard sqlGuard;
    @Autowired private SqlExecutor sqlExecutor;
    @Autowired private QueryPlanService queryPlanService;
    @Autowired private ChartRecommender chartRecommender;
    @Autowired private ResultInterpreter resultInterpreter;

    @Test
    void mysqlAndPostgresqlAccountsPlansResultsAndChartsAreEquivalentAndReadOnly() throws Exception {
        DataSourceConfig mysql = config(71001L, "mysql", envPort("CHATBI_IT_MYSQL_PORT", 19032));
        DataSourceConfig postgres = config(71002L, "postgresql", envPort("CHATBI_IT_POSTGRES_PORT", 19033));

        ConnectionTestResult mysqlVerification = verify(mysql);
        ConnectionTestResult postgresVerification = verify(postgres);
        assertThat(mysqlVerification.evidence()).anyMatch(line -> line.contains("SELECT"));
        assertThat(postgresVerification.evidence())
                .contains("role has no elevated cluster capability", "write-capable tables=0", "writable schemas=0");

        assertSchema(mysql);
        assertSchema(postgres);

        QueryExecResult mysqlAggregate = executeAggregate(mysql);
        QueryExecResult postgresAggregate = executeAggregate(postgres);
        assertAggregate(mysqlAggregate);
        assertAggregate(postgresAggregate);
        assertThat(exact(mysqlAggregate.getRows().get(0).get("paid_order_count")))
                .isEqualTo(exact(postgresAggregate.getRows().get(0).get("paid_order_count")));
        assertThat(exact(mysqlAggregate.getRows().get(0).get("paid_order_amount")))
                .isEqualTo(exact(postgresAggregate.getRows().get(0).get("paid_order_amount")));

        assertTrendChart(mysql, """
                SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS month,
                       SUM(o.total_amount) AS total_sales
                FROM orders o WHERE o.status='paid'
                GROUP BY DATE_FORMAT(o.order_date, '%Y-%m') ORDER BY month
                """);
        assertTrendChart(postgres, """
                SELECT TO_CHAR(o.order_date, 'YYYY-MM') AS month,
                       SUM(o.total_amount) AS total_sales
                FROM orders o WHERE o.status='paid'
                GROUP BY TO_CHAR(o.order_date, 'YYYY-MM') ORDER BY month
                """);

        assertDatabaseRejectsWrite(mysql);
        assertDatabaseRejectsWrite(postgres);
        assertThatThrownBy(() -> sqlGuard.sanitize("UPDATE orders SET status='paid'"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sqlGuard.sanitize("SELECT pg_read_file('/etc/passwd')"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sqlGuard.sanitize("SELECT * FROM orders FOR UPDATE"))
                .isInstanceOf(BusinessException.class);
    }

    private ConnectionTestResult verify(DataSourceConfig config) {
        ConnectionTestResult verification = connectionManager.testConnection(config);
        assertThat(verification.connected()).isTrue();
        assertThat(verification.readOnlyVerified()).isTrue();
        config.setVerifiedReadOnly(1);
        return verification;
    }

    private void assertSchema(DataSourceConfig config) {
        SchemaInfo schema = schemaInspector.inspect(config, true);
        assertThat(schema.getTables()).extracting(table -> table.getName().toLowerCase())
                .contains("customers", "products", "orders", "order_items");
    }

    private QueryExecResult executeAggregate(DataSourceConfig config) {
        String sql = sqlGuard.sanitize("""
                SELECT COUNT(*) AS paid_order_count,
                       SUM(o.total_amount) AS paid_order_amount
                FROM orders o WHERE o.status='paid'
                """);
        QueryRisk risk = queryPlanService.analyze(config, sql);
        assertThat(risk.blocked()).isFalse();
        assertThat(risk.planSummary()).isNotEmpty();
        return sqlExecutor.execute(config, sql, 5000);
    }

    private void assertAggregate(QueryExecResult result) {
        assertThat(result.getRows()).hasSize(1);
        Map<String, Object> row = result.getRows().get(0);
        assertThat(exact(row.get("paid_order_count"))).isEqualTo("20");
        assertThat(new BigDecimal(exact(row.get("paid_order_amount"))))
                .isEqualByComparingTo(new BigDecimal("185551.00"));
        var chart = chartRecommender.recommend(result.getColumns(), result.getRows());
        assertThat(chart.getType()).isEqualTo("table");
        var summary = resultInterpreter.interpret(result.getColumns(), result.getRows(), result.isTruncated());
        assertThat(summary.text()).contains("20", "185551.00");
    }

    private void assertTrendChart(DataSourceConfig config, String rawSql) {
        QueryExecResult result = sqlExecutor.execute(config, sqlGuard.sanitize(rawSql), 5000);
        var chart = chartRecommender.recommend(result.getColumns(), result.getRows());
        assertThat(result.getRows()).isNotEmpty();
        assertThat(chart.getType()).isEqualTo("line");
        assertThat(chart.getXField()).isEqualTo("month");
        assertThat(chart.getYFields()).containsExactly("total_sales");
        assertThat(result.getRows()).allSatisfy(row -> {
            assertThat(row).containsKeys(chart.getXField(), chart.getYFields().get(0));
            assertThat(row.get(chart.getYFields().get(0))).isNotNull();
        });
    }

    private void assertDatabaseRejectsWrite(DataSourceConfig config) throws Exception {
        try (Connection connection = connectionManager.getPool(config).getConnection();
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE orders SET status='paid' WHERE id=-1"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private DataSourceConfig config(Long id, String dbType, int port) {
        DataSourceConfig config = new DataSourceConfig();
        config.setId(id);
        config.setName("Real " + dbType);
        config.setDbType(dbType);
        config.setHost(System.getenv().getOrDefault("CHATBI_IT_HOST", "127.0.0.1"));
        config.setPort(port);
        config.setDatabaseName("chatbi_demo");
        config.setUsername("chatbi_ro");
        config.setPassword(System.getenv().getOrDefault("DEMO_READONLY_PASSWORD", "ChatBI!Readonly123"));
        config.setJdbcParams("");
        return config;
    }

    private String exact(Object value) {
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return String.valueOf(value);
    }

    private int envPort(String name, int defaultPort) {
        return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(defaultPort)));
    }
}
