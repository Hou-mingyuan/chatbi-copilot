package com.chatbi.copilot.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.service.DataSourceService;
import com.chatbi.copilot.permission.ColumnRef;
import com.chatbi.copilot.permission.dto.AccessGrantRequest;
import com.chatbi.copilot.permission.dto.AccessGrantVo;
import com.chatbi.copilot.permission.dto.ColumnRefRequest;
import com.chatbi.copilot.permission.dto.SensitivePolicyRequest;
import com.chatbi.copilot.permission.entity.ColumnAcl;
import com.chatbi.copilot.permission.entity.ColumnPolicy;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.entity.TableAcl;
import com.chatbi.copilot.permission.mapper.ColumnAclMapper;
import com.chatbi.copilot.permission.mapper.ColumnPolicyMapper;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.permission.mapper.TableAclMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPermissionService {
    private final DatasourceAclMapper datasourceAclMapper;
    private final TableAclMapper tableAclMapper;
    private final ColumnPolicyMapper columnPolicyMapper;
    private final ColumnAclMapper columnAclMapper;
    private final AppUserMapper userMapper;
    private final DataSourceService dataSourceService;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public AdminPermissionService(DatasourceAclMapper datasourceAclMapper, TableAclMapper tableAclMapper,
                                  ColumnPolicyMapper columnPolicyMapper, ColumnAclMapper columnAclMapper,
                                  AppUserMapper userMapper, DataSourceService dataSourceService,
                                  CurrentUserService currentUser, AuditService audit) {
        this.datasourceAclMapper = datasourceAclMapper;
        this.tableAclMapper = tableAclMapper;
        this.columnPolicyMapper = columnPolicyMapper;
        this.columnAclMapper = columnAclMapper;
        this.userMapper = userMapper;
        this.dataSourceService = dataSourceService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public AccessGrantVo getGrant(Long userId, Long datasourceId) {
        requireUser(userId);
        dataSourceService.getConfigForAdmin(datasourceId);
        DatasourceAcl acl = datasourceAclMapper.selectOne(new LambdaQueryWrapper<DatasourceAcl>()
                .eq(DatasourceAcl::getUserId, userId)
                .eq(DatasourceAcl::getDatasourceId, datasourceId));
        Set<String> tables = tableAclMapper.selectList(new LambdaQueryWrapper<TableAcl>()
                        .eq(TableAcl::getUserId, userId)
                        .eq(TableAcl::getDatasourceId, datasourceId))
                .stream().map(TableAcl::getTableName).collect(Collectors.toUnmodifiableSet());
        Set<ColumnRefRequest> columns = columnAclMapper.selectList(new LambdaQueryWrapper<ColumnAcl>()
                        .eq(ColumnAcl::getUserId, userId)
                        .eq(ColumnAcl::getDatasourceId, datasourceId))
                .stream().map(row -> new ColumnRefRequest(row.getTableName(), row.getColumnName()))
                .collect(Collectors.toUnmodifiableSet());
        return new AccessGrantVo(userId, datasourceId,
                acl != null && Integer.valueOf(1).equals(acl.getCanQuery()),
                acl != null && Integer.valueOf(1).equals(acl.getCanExport()),
                acl != null && Integer.valueOf(1).equals(acl.getCanManageSemantic()), tables, columns);
    }

    @Transactional
    public AccessGrantVo replaceGrant(Long userId, Long datasourceId, AccessGrantRequest request) {
        requireUser(userId);
        if (request.isCanExport() && !request.isCanQuery()) {
            throw new BusinessException("Export permission requires query permission");
        }
        if (request.isCanManageSemantic() && !request.isCanQuery()) {
            throw new BusinessException("Semantic management permission requires query permission");
        }
        SchemaIndex schema = schemaIndex(dataSourceService.getSchemaForAdmin(datasourceId, false));
        Set<String> tables = request.getTables().stream().map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
        if (request.isCanQuery() && tables.isEmpty()) {
            throw new BusinessException("At least one table is required when query access is enabled");
        }
        if (!schema.tables.containsAll(tables)) {
            throw new BusinessException("Permission contains a table that does not exist in the datasource schema");
        }
        Set<ColumnRef> sensitivePolicy = currentSensitive(datasourceId);
        Set<ColumnRef> grants = request.getSensitiveColumns().stream()
                .map(ref -> new ColumnRef(ref.table(), ref.column()))
                .collect(Collectors.toUnmodifiableSet());
        if (!sensitivePolicy.containsAll(grants)) {
            throw new BusinessException("Sensitive column grant must reference an existing sensitive policy");
        }
        if (grants.stream().anyMatch(ref -> !tables.contains(ref.table()))) {
            throw new BusinessException("Sensitive column grant requires access to its table");
        }

        datasourceAclMapper.delete(new LambdaQueryWrapper<DatasourceAcl>()
                .eq(DatasourceAcl::getUserId, userId)
                .eq(DatasourceAcl::getDatasourceId, datasourceId));
        tableAclMapper.delete(new LambdaQueryWrapper<TableAcl>()
                .eq(TableAcl::getUserId, userId)
                .eq(TableAcl::getDatasourceId, datasourceId));
        columnAclMapper.delete(new LambdaQueryWrapper<ColumnAcl>()
                .eq(ColumnAcl::getUserId, userId)
                .eq(ColumnAcl::getDatasourceId, datasourceId));

        DatasourceAcl acl = new DatasourceAcl();
        acl.setUserId(userId);
        acl.setDatasourceId(datasourceId);
        acl.setCanQuery(request.isCanQuery() ? 1 : 0);
        acl.setCanExport(request.isCanExport() ? 1 : 0);
        acl.setCanManageSemantic(request.isCanManageSemantic() ? 1 : 0);
        acl.setGrantedBy(currentUser.required().userId());
        acl.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        acl.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        datasourceAclMapper.insert(acl);
        for (String table : tables) {
            TableAcl row = new TableAcl();
            row.setUserId(userId);
            row.setDatasourceId(datasourceId);
            row.setTableName(table);
            row.setGrantedBy(currentUser.required().userId());
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            tableAclMapper.insert(row);
        }
        for (ColumnRef grant : grants) {
            ColumnAcl row = new ColumnAcl();
            row.setUserId(userId);
            row.setDatasourceId(datasourceId);
            row.setTableName(grant.table());
            row.setColumnName(grant.column());
            row.setGrantedBy(currentUser.required().userId());
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            columnAclMapper.insert(row);
        }
        audit.record("PERMISSION_REPLACE", "DATASOURCE", datasourceId, "SUCCESS",
                Map.of("targetUserId", userId, "tableCount", tables.size(),
                        "sensitiveColumnGrantCount", grants.size()));
        return getGrant(userId, datasourceId);
    }

    public Set<ColumnRefRequest> getSensitivePolicies(Long datasourceId) {
        dataSourceService.getConfigForAdmin(datasourceId);
        return currentSensitive(datasourceId).stream()
                .map(ref -> new ColumnRefRequest(ref.table(), ref.column()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public Set<ColumnRefRequest> replaceSensitivePolicies(Long datasourceId, SensitivePolicyRequest request) {
        SchemaIndex schema = schemaIndex(dataSourceService.getSchemaForAdmin(datasourceId, false));
        Set<ColumnRef> requested = request.getColumns().stream()
                .map(ref -> new ColumnRef(ref.table(), ref.column()))
                .collect(Collectors.toUnmodifiableSet());
        if (!schema.columns.containsAll(requested)) {
            throw new BusinessException("Sensitive policy references a column that does not exist");
        }
        columnPolicyMapper.delete(new LambdaQueryWrapper<ColumnPolicy>()
                .eq(ColumnPolicy::getDatasourceId, datasourceId));
        for (ColumnRef ref : requested) {
            ColumnPolicy policy = new ColumnPolicy();
            policy.setDatasourceId(datasourceId);
            policy.setTableName(ref.table());
            policy.setColumnName(ref.column());
            policy.setSensitive(1);
            policy.setLabel("Sensitive");
            policy.setUpdatedBy(currentUser.required().userId());
            policy.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            columnPolicyMapper.insert(policy);
        }
        // Policy replacement is security-sensitive: revoke all existing column grants so no
        // stale grant survives a changed classification.
        columnAclMapper.delete(new LambdaQueryWrapper<ColumnAcl>()
                .eq(ColumnAcl::getDatasourceId, datasourceId));
        audit.record("SENSITIVE_POLICY_REPLACE", "DATASOURCE", datasourceId, "SUCCESS",
                Map.of("sensitiveColumnCount", requested.size()));
        return getSensitivePolicies(datasourceId);
    }

    private Set<ColumnRef> currentSensitive(Long datasourceId) {
        return columnPolicyMapper.selectList(new LambdaQueryWrapper<ColumnPolicy>()
                        .eq(ColumnPolicy::getDatasourceId, datasourceId)
                        .eq(ColumnPolicy::getSensitive, 1))
                .stream().map(row -> new ColumnRef(row.getTableName(), row.getColumnName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private SchemaIndex schemaIndex(SchemaInfo schema) {
        Set<String> tables = new HashSet<>();
        Set<ColumnRef> columns = new HashSet<>();
        schema.getTables().forEach(table -> {
            String name = normalize(table.getName());
            tables.add(name);
            table.getColumns().forEach(column -> columns.add(new ColumnRef(name, column.getName())));
        });
        return new SchemaIndex(Set.copyOf(tables), Set.copyOf(columns));
    }

    private void requireUser(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("User not found");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record SchemaIndex(Set<String> tables, Set<ColumnRef> columns) {
    }
}
