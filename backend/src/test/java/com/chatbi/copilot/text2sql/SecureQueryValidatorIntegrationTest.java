package com.chatbi.copilot.text2sql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.entity.ColumnPolicy;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.entity.TableAcl;
import com.chatbi.copilot.permission.mapper.ColumnPolicyMapper;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.permission.mapper.TableAclMapper;
import com.chatbi.copilot.text2sql.service.SecureQueryValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:validator-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "server.port=0"
})
@Transactional
class SecureQueryValidatorIntegrationTest {
    @Autowired private SecureQueryValidator validator;
    @Autowired private AppUserMapper userMapper;
    @Autowired private DatasourceAclMapper datasourceAclMapper;
    @Autowired private TableAclMapper tableAclMapper;
    @Autowired private ColumnPolicyMapper columnPolicyMapper;

    private AppUser analyst;
    private final Long datasourceId = 91234L;

    @BeforeEach
    void setUp() {
        analyst = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, "analyst"));
        AppUserPrincipal principal = new AppUserPrincipal(analyst.getId(), analyst.getUsername(),
                analyst.getDisplayName(), "test-session", "csrf", Set.of(Role.ANALYST));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        DatasourceAcl acl = new DatasourceAcl();
        acl.setUserId(analyst.getId());
        acl.setDatasourceId(datasourceId);
        acl.setCanQuery(1);
        acl.setCanExport(0);
        acl.setCanManageSemantic(0);
        datasourceAclMapper.insert(acl);
        TableAcl table = new TableAcl();
        table.setUserId(analyst.getId());
        table.setDatasourceId(datasourceId);
        table.setTableName("customers");
        tableAclMapper.insert(table);
        ColumnPolicy sensitive = new ColumnPolicy();
        sensitive.setDatasourceId(datasourceId);
        sensitive.setTableName("customers");
        sensitive.setColumnName("name");
        sensitive.setSensitive(1);
        columnPolicyMapper.insert(sensitive);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validatesAllowedResourcesAndAddsBoundedLimit() {
        var result = validator.validate(
                "SELECT region, COUNT(*) total FROM customers GROUP BY region",
                datasourceId, Capability.QUERY, schema());
        assertThat(result.tables()).containsExactly("customers");
        assertThat(result.sql()).containsIgnoringCase("LIMIT 500");
    }

    @Test
    void deniesSensitiveColumnsThroughDirectWildcardAndCtePaths() {
        assertThatThrownBy(() -> validator.validate("SELECT name FROM customers",
                datasourceId, Capability.QUERY, schema()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> validator.validate("SELECT * FROM customers",
                datasourceId, Capability.QUERY, schema()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("SELECT *");
        assertThatThrownBy(() -> validator.validate(
                "WITH c AS (SELECT name FROM customers) SELECT * FROM c",
                datasourceId, Capability.QUERY, schema()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deniesUnknownTablesAndExportCapability() {
        assertThatThrownBy(() -> validator.validate("SELECT * FROM payments",
                datasourceId, Capability.QUERY, schema()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> validator.validate("SELECT region FROM customers",
                datasourceId, Capability.EXPORT, schema()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("permission");
    }

    private SchemaInfo schema() {
        SchemaInfo schema = new SchemaInfo();
        schema.setDatasourceId(datasourceId);
        schema.setDbType("mysql");
        schema.setDatabaseName("demo");
        TableSchema table = new TableSchema();
        table.setName("customers");
        table.getColumns().add(column("id", "BIGINT"));
        table.getColumns().add(column("name", "VARCHAR"));
        table.getColumns().add(column("region", "VARCHAR"));
        schema.getTables().add(table);
        return schema;
    }

    private ColumnSchema column(String name, String type) {
        ColumnSchema column = new ColumnSchema();
        column.setName(name);
        column.setDataType(type);
        return column;
    }
}
