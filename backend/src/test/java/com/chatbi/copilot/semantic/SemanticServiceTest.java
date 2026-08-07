package com.chatbi.copilot.semantic;

import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.datasource.service.SchemaInspector;
import com.chatbi.copilot.permission.DataAccessPolicy;
import com.chatbi.copilot.semantic.dto.SemanticModelReq;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.mapper.SemanticModelMapper;
import com.chatbi.copilot.semantic.mapper.SemanticRevisionMapper;
import com.chatbi.copilot.semantic.service.SemanticService;
import com.chatbi.copilot.text2sql.service.SqlGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticServiceTest {
    @Mock private SemanticModelMapper mapper;
    @Mock private SemanticRevisionMapper revisionMapper;
    @Mock private CurrentUserService currentUser;
    @Mock private DataAccessPolicy accessPolicy;
    @Mock private AuditService audit;
    @Mock private DataSourceConfigMapper datasourceMapper;
    @Mock private SchemaInspector schemaInspector;

    private SemanticService service;

    @BeforeEach
    void setUp() {
        SqlGuardProperties properties = new SqlGuardProperties();
        service = new SemanticService(mapper, revisionMapper, new ObjectMapper(), currentUser,
                accessPolicy, audit, datasourceMapper, schemaInspector, new SqlGuard(properties));
        when(currentUser.required()).thenReturn(new AppUserPrincipal(1L, "analyst", "Analyst",
                "session", "csrf", Set.of(Role.ANALYST)));
        DataSourceConfig config = new DataSourceConfig();
        config.setId(1L);
        config.setVerifiedReadOnly(1);
        when(datasourceMapper.selectById(1L)).thenReturn(config);
        when(schemaInspector.inspect(config, false)).thenReturn(schema());
        lenient().when(mapper.selectCount(any())).thenReturn(0L);
        lenient().doAnswer(invocation -> {
            SemanticModel model = invocation.getArgument(0);
            model.setId(10L);
            return 1;
        }).when(mapper).insert(any(SemanticModel.class));
    }

    @Test
    void acceptsMetricOnlyWhenItsExpressionUsesExistingColumns() {
        SemanticModelReq request = base("METRIC", "order_items");
        request.setBusinessAlias("销售额");
        request.setMetricExpression("SUM(order_items.amount)");
        request.setAggregation("SUM");

        SemanticModel created = service.create(request);

        assertThat(created.getMetricExpression()).isEqualTo("SUM(order_items.amount)");
        assertThat(created.getVersion()).isEqualTo(1);

        request.setMetricExpression("SUM(order_items.missing_amount)");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsMissingTablesJoinEndpointsAndNonTimeColumns() {
        SemanticModelReq missingTable = base("TABLE", "missing");
        missingTable.setBusinessAlias("Missing");
        assertThatThrownBy(() -> service.create(missingTable))
                .isInstanceOf(BusinessException.class).hasMessageContaining("tableName");

        SemanticModelReq join = base("JOIN", "orders");
        join.setColumnName("customer_id");
        join.setRelatedTable("customers");
        join.setRelatedColumn("missing");
        join.setJoinType("LEFT");
        assertThatThrownBy(() -> service.create(join))
                .isInstanceOf(BusinessException.class).hasMessageContaining("relatedColumn");

        SemanticModelReq time = base("TIME", "orders");
        time.setColumnName("status");
        time.setTimeGrain("MONTH");
        assertThatThrownBy(() -> service.create(time))
                .isInstanceOf(BusinessException.class).hasMessageContaining("date/time");
    }

    private SemanticModelReq base(String type, String table) {
        SemanticModelReq request = new SemanticModelReq();
        request.setDatasourceId(1L);
        request.setDefinitionType(type);
        request.setTableName(table);
        return request;
    }

    private SchemaInfo schema() {
        SchemaInfo schema = new SchemaInfo();
        schema.setDatasourceId(1L);
        schema.getTables().add(table("orders",
                column("id", "BIGINT"), column("customer_id", "BIGINT"),
                column("status", "VARCHAR"), column("order_date", "DATE")));
        schema.getTables().add(table("customers", column("id", "BIGINT"), column("name", "VARCHAR")));
        schema.getTables().add(table("order_items", column("order_id", "BIGINT"),
                column("amount", "DECIMAL")));
        return schema;
    }

    private TableSchema table(String name, ColumnSchema... columns) {
        TableSchema table = new TableSchema();
        table.setName(name);
        table.getColumns().addAll(java.util.List.of(columns));
        return table;
    }

    private ColumnSchema column(String name, String type) {
        ColumnSchema column = new ColumnSchema();
        column.setName(name);
        column.setDataType(type);
        return column;
    }
}
