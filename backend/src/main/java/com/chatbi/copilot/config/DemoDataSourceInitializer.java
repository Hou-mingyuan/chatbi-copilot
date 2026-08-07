package com.chatbi.copilot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.dto.ConnectionTestResult;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.datasource.service.DynamicConnectionManager;
import com.chatbi.copilot.permission.entity.ColumnAcl;
import com.chatbi.copilot.permission.entity.ColumnPolicy;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.entity.TableAcl;
import com.chatbi.copilot.permission.mapper.ColumnAclMapper;
import com.chatbi.copilot.permission.mapper.ColumnPolicyMapper;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.permission.mapper.TableAclMapper;
import com.chatbi.copilot.semantic.SemanticDefinitionType;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.entity.SemanticRevision;
import com.chatbi.copilot.semantic.mapper.SemanticModelMapper;
import com.chatbi.copilot.semantic.mapper.SemanticRevisionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DemoDataSourceInitializer implements ApplicationRunner {
    private static final List<String> DEMO_TABLES = List.of("customers", "products", "orders", "order_items");

    private final DemoDataSourceProperties demo;
    private final DataSourceConfigMapper datasourceMapper;
    private final PasswordCipher cipher;
    private final DynamicConnectionManager connectionManager;
    private final AppUserMapper userMapper;
    private final DatasourceAclMapper datasourceAclMapper;
    private final TableAclMapper tableAclMapper;
    private final ColumnPolicyMapper columnPolicyMapper;
    private final ColumnAclMapper columnAclMapper;
    private final SemanticModelMapper semanticMapper;
    private final SemanticRevisionMapper semanticRevisionMapper;
    private final ObjectMapper objectMapper;

    public DemoDataSourceInitializer(DemoDataSourceProperties demo,
                                     DataSourceConfigMapper datasourceMapper,
                                     PasswordCipher cipher,
                                     DynamicConnectionManager connectionManager,
                                     AppUserMapper userMapper,
                                     DatasourceAclMapper datasourceAclMapper,
                                     TableAclMapper tableAclMapper,
                                     ColumnPolicyMapper columnPolicyMapper,
                                     ColumnAclMapper columnAclMapper,
                                     SemanticModelMapper semanticMapper,
                                     SemanticRevisionMapper semanticRevisionMapper,
                                     ObjectMapper objectMapper) {
        this.demo = demo;
        this.datasourceMapper = datasourceMapper;
        this.cipher = cipher;
        this.connectionManager = connectionManager;
        this.userMapper = userMapper;
        this.datasourceAclMapper = datasourceAclMapper;
        this.tableAclMapper = tableAclMapper;
        this.columnPolicyMapper = columnPolicyMapper;
        this.columnAclMapper = columnAclMapper;
        this.semanticMapper = semanticMapper;
        this.semanticRevisionMapper = semanticRevisionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demo.isEnabled()) {
            return;
        }
        AppUser admin = user("admin");
        AppUser analyst = user("analyst");
        AppUser viewer = user("viewer");

        DataSourceConfig mysql = ensureDatasource(demo.getName(), "mysql", demo.getHost(), demo.getPort(),
                demo.getDatabaseName(), demo.getUsername(), demo.getPassword(), admin);
        seedAccessAndSemantics(mysql, admin, analyst, viewer);

        if (demo.isPostgresEnabled()) {
            DataSourceConfig postgres = ensureDatasource(demo.getPostgresName(), "postgresql",
                    demo.getPostgresHost(), demo.getPostgresPort(), demo.getPostgresDatabaseName(),
                    demo.getPostgresUsername(), demo.getPostgresPassword(), admin);
            seedAccessAndSemantics(postgres, admin, analyst, viewer);
        }
    }

    private DataSourceConfig ensureDatasource(String name, String dbType, String host, int port,
                                              String database, String username, String password,
                                              AppUser admin) {
        DataSourceConfig config = datasourceMapper.selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getName, name));
        boolean created = config == null;
        if (created) {
            config = new DataSourceConfig();
            config.setName(name);
            config.setCreatedBy(admin == null ? null : admin.getId());
        } else {
            connectionManager.evict(config.getId());
        }
        config.setDbType(dbType);
        config.setHost(host);
        config.setPort(port);
        config.setDatabaseName(database);
        config.setUsername(username);
        config.setPassword(cipher.encrypt(password));
        config.setJdbcParams("");
        config.setRemark("Local zero-key demo; database account verified SELECT-only");

        ConnectionTestResult verification = connectionManager.testConnection(config);
        config.setVerifiedReadOnly(verification.readOnlyVerified() ? 1 : 0);
        config.setLastVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (created) {
            datasourceMapper.insert(config);
        } else {
            datasourceMapper.updateById(config);
        }
        log.info("Verified and {} demo datasource '{}' ({})", created ? "registered" : "updated", name, dbType);
        return config;
    }

    private void seedAccessAndSemantics(DataSourceConfig datasource, AppUser admin,
                                        AppUser analyst, AppUser viewer) {
        ensureSensitivePolicy(datasource.getId(), admin == null ? null : admin.getId(), "customers", "name", "客户姓名");
        if (analyst != null) {
            ensureDatasourceAcl(analyst.getId(), datasource.getId(), true, true, true,
                    admin == null ? null : admin.getId());
            DEMO_TABLES.forEach(table -> ensureTableAcl(analyst.getId(), datasource.getId(), table,
                    admin == null ? null : admin.getId()));
            ensureColumnAcl(analyst.getId(), datasource.getId(), "customers", "name",
                    admin == null ? null : admin.getId());
        }
        if (viewer != null) {
            ensureDatasourceAcl(viewer.getId(), datasource.getId(), true, false, false,
                    admin == null ? null : admin.getId());
            DEMO_TABLES.forEach(table -> ensureTableAcl(viewer.getId(), datasource.getId(), table,
                    admin == null ? null : admin.getId()));
        }
        Long actor = admin == null ? null : admin.getId();
        ensureSemantic(datasource.getId(), SemanticDefinitionType.TABLE, "orders", null,
                "订单", "销售订单；只有 status=paid 计入已支付业务口径", null, null, null,
                null, null, null, null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.COLUMN, "orders", "status",
                "订单状态", "paid=已支付，pending=待支付，refunded=已退款", null, null, null,
                null, null, null, null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.METRIC, "order_items", "amount",
                "销售额", "默认销售额为已支付订单明细金额之和", "SUM(order_items.amount)", "SUM", "元",
                null, null, null, null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.TIME, "orders", "order_date",
                "下单时间", "默认趋势按月聚合", null, null, null,
                "MONTH", null, null, null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.ENUM, "orders", "status",
                "已支付", "已支付订单", null, null, null,
                null, "paid", "已支付", null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.ENUM, "orders", "status",
                "已退款", "已退款订单", null, null, null,
                null, "refunded", "已退款", null, null, actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.JOIN, "orders", "customer_id",
                "订单客户", "orders.customer_id 关联 customers.id", null, null, null,
                null, null, null, "customers", "id", actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.JOIN, "order_items", "order_id",
                "订单明细", "order_items.order_id 关联 orders.id", null, null, null,
                null, null, null, "orders", "id", actor);
        ensureSemantic(datasource.getId(), SemanticDefinitionType.JOIN, "order_items", "product_id",
                "产品明细", "order_items.product_id 关联 products.id", null, null, null,
                null, null, null, "products", "id", actor);
    }

    private void ensureDatasourceAcl(Long userId, Long datasourceId, boolean query, boolean export,
                                     boolean semantic, Long grantedBy) {
        Long count = datasourceAclMapper.selectCount(new LambdaQueryWrapper<DatasourceAcl>()
                .eq(DatasourceAcl::getUserId, userId).eq(DatasourceAcl::getDatasourceId, datasourceId));
        if (count != null && count > 0) return;
        DatasourceAcl row = new DatasourceAcl();
        row.setUserId(userId);
        row.setDatasourceId(datasourceId);
        row.setCanQuery(query ? 1 : 0);
        row.setCanExport(export ? 1 : 0);
        row.setCanManageSemantic(semantic ? 1 : 0);
        row.setGrantedBy(grantedBy);
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        row.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        datasourceAclMapper.insert(row);
    }

    private void ensureTableAcl(Long userId, Long datasourceId, String table, Long grantedBy) {
        Long count = tableAclMapper.selectCount(new LambdaQueryWrapper<TableAcl>()
                .eq(TableAcl::getUserId, userId).eq(TableAcl::getDatasourceId, datasourceId)
                .eq(TableAcl::getTableName, table));
        if (count != null && count > 0) return;
        TableAcl row = new TableAcl();
        row.setUserId(userId);
        row.setDatasourceId(datasourceId);
        row.setTableName(table);
        row.setGrantedBy(grantedBy);
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        tableAclMapper.insert(row);
    }

    private void ensureSensitivePolicy(Long datasourceId, Long actor, String table, String column, String label) {
        Long count = columnPolicyMapper.selectCount(new LambdaQueryWrapper<ColumnPolicy>()
                .eq(ColumnPolicy::getDatasourceId, datasourceId).eq(ColumnPolicy::getTableName, table)
                .eq(ColumnPolicy::getColumnName, column));
        if (count != null && count > 0) return;
        ColumnPolicy row = new ColumnPolicy();
        row.setDatasourceId(datasourceId);
        row.setTableName(table);
        row.setColumnName(column);
        row.setSensitive(1);
        row.setLabel(label);
        row.setUpdatedBy(actor);
        row.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        columnPolicyMapper.insert(row);
    }

    private void ensureColumnAcl(Long userId, Long datasourceId, String table, String column, Long grantedBy) {
        Long count = columnAclMapper.selectCount(new LambdaQueryWrapper<ColumnAcl>()
                .eq(ColumnAcl::getUserId, userId).eq(ColumnAcl::getDatasourceId, datasourceId)
                .eq(ColumnAcl::getTableName, table).eq(ColumnAcl::getColumnName, column));
        if (count != null && count > 0) return;
        ColumnAcl row = new ColumnAcl();
        row.setUserId(userId);
        row.setDatasourceId(datasourceId);
        row.setTableName(table);
        row.setColumnName(column);
        row.setGrantedBy(grantedBy);
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        columnAclMapper.insert(row);
    }

    private void ensureSemantic(Long datasourceId, SemanticDefinitionType type, String table, String column,
                                String alias, String description, String expression, String aggregation, String unit,
                                String timeGrain, String enumValue, String enumLabel,
                                String relatedTable, String relatedColumn, Long actor) {
        LambdaQueryWrapper<SemanticModel> query = new LambdaQueryWrapper<SemanticModel>()
                .eq(SemanticModel::getDatasourceId, datasourceId)
                .eq(SemanticModel::getDefinitionType, type.name())
                .eq(SemanticModel::getTableName, table)
                .eq(column != null, SemanticModel::getColumnName, column)
                .eq(enumValue != null, SemanticModel::getEnumValue, enumValue)
                .eq(relatedTable != null, SemanticModel::getRelatedTable, relatedTable);
        SemanticModel model = semanticMapper.selectOne(query.last("LIMIT 1"));
        if (model == null) {
            model = new SemanticModel();
            model.setDatasourceId(datasourceId);
            model.setDefinitionType(type.name());
            model.setTableName(table);
            model.setColumnName(column);
            model.setBusinessAlias(alias);
            model.setDescription(description);
            model.setMetricExpression(expression);
            model.setAggregation(aggregation);
            model.setUnit(unit);
            model.setTimeGrain(timeGrain);
            model.setEnumValue(enumValue);
            model.setEnumLabel(enumLabel);
            model.setRelatedTable(relatedTable);
            model.setRelatedColumn(relatedColumn);
            model.setJoinType(type == SemanticDefinitionType.JOIN ? "INNER" : null);
            model.setVersion(1);
            model.setActive(1);
            model.setCreatedBy(actor);
            model.setUpdatedBy(actor);
            semanticMapper.insert(model);
        }
        ensureSemanticRevision(model, actor);
    }

    private void ensureSemanticRevision(SemanticModel model, Long actor) {
        Long count = semanticRevisionMapper.selectCount(new LambdaQueryWrapper<SemanticRevision>()
                .eq(SemanticRevision::getSemanticId, model.getId())
                .eq(SemanticRevision::getVersion, model.getVersion()));
        if (count != null && count > 0) return;
        SemanticRevision revision = new SemanticRevision();
        revision.setSemanticId(model.getId());
        revision.setDatasourceId(model.getDatasourceId());
        revision.setVersion(model.getVersion());
        revision.setDefinitionType(model.getDefinitionType());
        try {
            revision.setSnapshot(objectMapper.writeValueAsString(model));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Demo semantic revision could not be serialized", e);
        }
        revision.setChangedBy(actor);
        revision.setChangeType("CREATE");
        semanticRevisionMapper.insert(revision);
    }

    private AppUser user(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
    }
}
