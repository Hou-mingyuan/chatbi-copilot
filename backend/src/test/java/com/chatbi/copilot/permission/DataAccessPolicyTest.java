package com.chatbi.copilot.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.permission.entity.ColumnAcl;
import com.chatbi.copilot.permission.entity.ColumnPolicy;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.entity.TableAcl;
import com.chatbi.copilot.permission.mapper.ColumnAclMapper;
import com.chatbi.copilot.permission.mapper.ColumnPolicyMapper;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.permission.mapper.TableAclMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:permission-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "server.port=0"
})
@Transactional
class DataAccessPolicyTest {
    @Autowired private DataAccessPolicy policy;
    @Autowired private AppUserMapper userMapper;
    @Autowired private DataSourceConfigMapper datasourceMapper;
    @Autowired private DatasourceAclMapper datasourceAclMapper;
    @Autowired private TableAclMapper tableAclMapper;
    @Autowired private ColumnPolicyMapper columnPolicyMapper;
    @Autowired private ColumnAclMapper columnAclMapper;

    private AppUserPrincipal analyst;
    private Long datasourceId;

    @BeforeEach
    void setUp() {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, "analyst"));
        analyst = new AppUserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                "test-session", "csrf", Set.of(Role.ANALYST));

        DataSourceConfig datasource = new DataSourceConfig();
        datasource.setName("Policy test");
        datasource.setDbType("mysql");
        datasource.setHost("localhost");
        datasource.setPort(3306);
        datasource.setDatabaseName("demo");
        datasource.setUsername("readonly");
        datasource.setPassword("encrypted");
        datasource.setVerifiedReadOnly(1);
        datasourceMapper.insert(datasource);
        datasourceId = datasource.getId();

        DatasourceAcl datasourceAcl = new DatasourceAcl();
        datasourceAcl.setUserId(user.getId());
        datasourceAcl.setDatasourceId(datasourceId);
        datasourceAcl.setCanQuery(1);
        datasourceAcl.setCanExport(0);
        datasourceAcl.setCanManageSemantic(0);
        datasourceAclMapper.insert(datasourceAcl);

        TableAcl tableAcl = new TableAcl();
        tableAcl.setUserId(user.getId());
        tableAcl.setDatasourceId(datasourceId);
        tableAcl.setTableName("customers");
        tableAclMapper.insert(tableAcl);

        ColumnPolicy sensitive = new ColumnPolicy();
        sensitive.setDatasourceId(datasourceId);
        sensitive.setTableName("customers");
        sensitive.setColumnName("name");
        sensitive.setSensitive(1);
        columnPolicyMapper.insert(sensitive);
    }

    @Test
    void hidesSensitiveColumnsFromSchema() {
        SchemaInfo filtered = policy.filterSchema(analyst, datasourceId, schema());
        assertThat(filtered.getTables()).extracting(TableSchema::getName).containsExactly("customers");
        assertThat(filtered.getTables().get(0).getColumns())
                .extracting(ColumnSchema::getName).containsExactly("id", "region");
    }

    @Test
    void deniesSensitiveColumnAndProjectionWildcard() {
        assertThatThrownBy(() -> policy.authorizeResources(analyst, datasourceId, Capability.QUERY,
                Set.of("customers"), Set.of(new ColumnRef("customers", "name")), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sensitive");
        assertThatThrownBy(() -> policy.authorizeResources(analyst, datasourceId, Capability.QUERY,
                Set.of("customers"), Set.of(), Set.of("customers")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SELECT *");
    }

    @Test
    void explicitSensitiveGrantAllowsColumnButExportStillDenied() {
        ColumnAcl grant = new ColumnAcl();
        grant.setUserId(analyst.userId());
        grant.setDatasourceId(datasourceId);
        grant.setTableName("customers");
        grant.setColumnName("name");
        columnAclMapper.insert(grant);

        policy.authorizeResources(analyst, datasourceId, Capability.QUERY,
                Set.of("customers"), Set.of(new ColumnRef("customers", "name")), Set.of("customers"));
        assertThatThrownBy(() -> policy.authorizeResources(analyst, datasourceId, Capability.EXPORT,
                Set.of("customers"), Set.of(), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void deniesUnlistedTable() {
        assertThatThrownBy(() -> policy.authorizeResources(analyst, datasourceId, Capability.QUERY,
                Set.of("orders"), Set.of(), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("table");
    }

    private SchemaInfo schema() {
        SchemaInfo schema = new SchemaInfo();
        schema.setDatasourceId(datasourceId);
        schema.setDatabaseName("demo");
        schema.setDbType("mysql");
        TableSchema customers = new TableSchema();
        customers.setName("customers");
        customers.getColumns().add(column("id", "BIGINT"));
        customers.getColumns().add(column("name", "VARCHAR"));
        customers.getColumns().add(column("region", "VARCHAR"));
        schema.getTables().add(customers);
        return schema;
    }

    private ColumnSchema column(String name, String type) {
        ColumnSchema column = new ColumnSchema();
        column.setName(name);
        column.setDataType(type);
        return column;
    }
}
