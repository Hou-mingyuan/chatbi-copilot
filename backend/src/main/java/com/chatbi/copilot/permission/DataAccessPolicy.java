package com.chatbi.copilot.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.permission.entity.ColumnAcl;
import com.chatbi.copilot.permission.entity.ColumnPolicy;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.entity.TableAcl;
import com.chatbi.copilot.permission.mapper.ColumnAclMapper;
import com.chatbi.copilot.permission.mapper.ColumnPolicyMapper;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.permission.mapper.TableAclMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DataAccessPolicy {
    private final DatasourceAclMapper datasourceAclMapper;
    private final TableAclMapper tableAclMapper;
    private final ColumnPolicyMapper columnPolicyMapper;
    private final ColumnAclMapper columnAclMapper;

    public DataAccessPolicy(DatasourceAclMapper datasourceAclMapper, TableAclMapper tableAclMapper,
                            ColumnPolicyMapper columnPolicyMapper, ColumnAclMapper columnAclMapper) {
        this.datasourceAclMapper = datasourceAclMapper;
        this.tableAclMapper = tableAclMapper;
        this.columnPolicyMapper = columnPolicyMapper;
        this.columnAclMapper = columnAclMapper;
    }

    public Set<Long> accessibleDatasourceIds(AppUserPrincipal user, Capability capability) {
        if (user.isAdmin()) {
            return Set.of();
        }
        List<DatasourceAcl> rows = datasourceAclMapper.selectList(
                new LambdaQueryWrapper<DatasourceAcl>().eq(DatasourceAcl::getUserId, user.userId()));
        Set<Long> ids = new HashSet<>();
        for (DatasourceAcl row : rows) {
            if (allowed(row, capability)) {
                ids.add(row.getDatasourceId());
            }
        }
        return Set.copyOf(ids);
    }

    public void requireDatasource(AppUserPrincipal user, Long datasourceId, Capability capability) {
        if (!hasDatasourceCapability(user, datasourceId, capability)) {
            throw BusinessException.forbidden("You do not have permission for this datasource operation");
        }
    }

    public boolean hasDatasourceCapability(AppUserPrincipal user, Long datasourceId, Capability capability) {
        if (user.isAdmin()) {
            return true;
        }
        DatasourceAcl acl = datasourceAclMapper.selectOne(new LambdaQueryWrapper<DatasourceAcl>()
                .eq(DatasourceAcl::getUserId, user.userId())
                .eq(DatasourceAcl::getDatasourceId, datasourceId));
        return acl != null && allowed(acl, capability);
    }

    public SchemaInfo filterSchema(AppUserPrincipal user, Long datasourceId, SchemaInfo source) {
        requireDatasource(user, datasourceId, Capability.QUERY);
        if (user.isAdmin()) {
            return copySchema(source, null, Set.of(), Set.of());
        }
        Set<String> allowedTables = allowedTables(user.userId(), datasourceId);
        Set<ColumnRef> sensitive = sensitiveColumns(datasourceId);
        Set<ColumnRef> allowedSensitive = allowedSensitiveColumns(user.userId(), datasourceId);
        return copySchema(source, allowedTables, sensitive, allowedSensitive);
    }

    public void authorizeResources(AppUserPrincipal user, Long datasourceId, Capability capability,
                                   Set<String> tables, Set<ColumnRef> columns,
                                   Set<String> projectionWildcards) {
        requireDatasource(user, datasourceId, capability);
        if (user.isAdmin()) {
            return;
        }
        Set<String> allowedTables = allowedTables(user.userId(), datasourceId);
        for (String table : tables) {
            if (!allowedTables.contains(normalize(table))) {
                throw BusinessException.forbidden("Query references a table you are not allowed to access");
            }
        }
        Set<ColumnRef> sensitive = sensitiveColumns(datasourceId);
        Set<ColumnRef> allowedSensitive = allowedSensitiveColumns(user.userId(), datasourceId);
        for (ColumnRef column : columns) {
            if (sensitive.contains(column) && !allowedSensitive.contains(column)) {
                throw BusinessException.forbidden("Query references a sensitive column you are not allowed to access");
            }
        }
        for (String wildcardTable : projectionWildcards) {
            String normalizedTable = normalize(wildcardTable);
            boolean exposesDeniedSensitive = sensitive.stream()
                    .filter(ref -> ref.table().equals(normalizedTable))
                    .anyMatch(ref -> !allowedSensitive.contains(ref));
            if (exposesDeniedSensitive) {
                throw BusinessException.forbidden("SELECT * would expose a restricted sensitive column");
            }
        }
    }

    public Set<String> allowedTables(Long userId, Long datasourceId) {
        return tableAclMapper.selectList(new LambdaQueryWrapper<TableAcl>()
                        .eq(TableAcl::getUserId, userId)
                        .eq(TableAcl::getDatasourceId, datasourceId))
                .stream().map(row -> normalize(row.getTableName()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<ColumnRef> sensitiveColumns(Long datasourceId) {
        return columnPolicyMapper.selectList(new LambdaQueryWrapper<ColumnPolicy>()
                        .eq(ColumnPolicy::getDatasourceId, datasourceId)
                        .eq(ColumnPolicy::getSensitive, 1))
                .stream().map(row -> new ColumnRef(row.getTableName(), row.getColumnName()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<ColumnRef> allowedSensitiveColumns(Long userId, Long datasourceId) {
        return columnAclMapper.selectList(new LambdaQueryWrapper<ColumnAcl>()
                        .eq(ColumnAcl::getUserId, userId)
                        .eq(ColumnAcl::getDatasourceId, datasourceId))
                .stream().map(row -> new ColumnRef(row.getTableName(), row.getColumnName()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean allowed(DatasourceAcl row, Capability capability) {
        return switch (capability) {
            case QUERY -> Integer.valueOf(1).equals(row.getCanQuery());
            case EXPORT -> Integer.valueOf(1).equals(row.getCanExport());
            case MANAGE_SEMANTIC -> Integer.valueOf(1).equals(row.getCanManageSemantic());
        };
    }

    private SchemaInfo copySchema(SchemaInfo source, Set<String> allowedTables,
                                  Set<ColumnRef> sensitive, Set<ColumnRef> allowedSensitive) {
        SchemaInfo copy = new SchemaInfo();
        copy.setDatasourceId(source.getDatasourceId());
        copy.setDatabaseName(source.getDatabaseName());
        copy.setDbType(source.getDbType());
        for (TableSchema table : source.getTables()) {
            String tableName = normalize(table.getName());
            if (allowedTables != null && !allowedTables.contains(tableName)) {
                continue;
            }
            TableSchema tableCopy = new TableSchema();
            tableCopy.setName(table.getName());
            tableCopy.setComment(table.getComment());
            tableCopy.setBusinessAlias(table.getBusinessAlias());
            tableCopy.setBusinessDescription(table.getBusinessDescription());
            for (ColumnSchema column : table.getColumns()) {
                ColumnRef ref = new ColumnRef(tableName, column.getName());
                if (sensitive.contains(ref) && !allowedSensitive.contains(ref)) {
                    continue;
                }
                ColumnSchema columnCopy = new ColumnSchema();
                columnCopy.setName(column.getName());
                columnCopy.setDataType(column.getDataType());
                columnCopy.setComment(column.getComment());
                columnCopy.setNullable(column.isNullable());
                columnCopy.setPrimaryKey(column.isPrimaryKey());
                columnCopy.setBusinessAlias(column.getBusinessAlias());
                columnCopy.setBusinessDescription(column.getBusinessDescription());
                tableCopy.getColumns().add(columnCopy);
            }
            copy.getTables().add(tableCopy);
        }
        return copy;
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }
}
