package com.chatbi.copilot.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.dto.DataSourceReq;
import com.chatbi.copilot.datasource.dto.DataSourceVo;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.ConnectionTestResult;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.semantic.service.SemanticService;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class DataSourceService {

    private final DataSourceConfigMapper mapper;
    private final DynamicConnectionManager connectionManager;
    private final SchemaInspector schemaInspector;
    private final SemanticService semanticService;
    private final PasswordCipher cipher;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;
    private final AuditService audit;

    public DataSourceService(DataSourceConfigMapper mapper,
                             DynamicConnectionManager connectionManager,
                             SchemaInspector schemaInspector,
                             SemanticService semanticService,
                             PasswordCipher cipher, CurrentUserService currentUser,
                             DataAccessPolicy accessPolicy, AuditService audit) {
        this.mapper = mapper;
        this.connectionManager = connectionManager;
        this.schemaInspector = schemaInspector;
        this.semanticService = semanticService;
        this.cipher = cipher;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
    }

    public List<DataSourceVo> list() {
        AppUserPrincipal user = currentUser.required();
        LambdaQueryWrapper<DataSourceConfig> wrapper = new LambdaQueryWrapper<DataSourceConfig>()
                .orderByDesc(DataSourceConfig::getId);
        if (!user.isAdmin()) {
            var ids = accessPolicy.accessibleDatasourceIds(user, Capability.QUERY);
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in(DataSourceConfig::getId, ids);
        }
        return mapper.selectList(wrapper).stream().map(config -> toVo(config, user)).toList();
    }

    public DataSourceConfig getConfigForQuery(Long id) {
        accessPolicy.requireDatasource(currentUser.required(), id, Capability.QUERY);
        return requireVerified(getConfigUnchecked(id));
    }

    public DataSourceConfig getConfigForExport(Long id) {
        accessPolicy.requireDatasource(currentUser.required(), id, Capability.EXPORT);
        return requireVerified(getConfigUnchecked(id));
    }

    public DataSourceConfig getConfigForAdmin(Long id) {
        if (!currentUser.required().isAdmin()) {
            throw BusinessException.forbidden("Administrator permission required");
        }
        return getConfigUnchecked(id);
    }

    public DataSourceConfig getConfigUnchecked(Long id) {
        DataSourceConfig config = mapper.selectById(id);
        if (config == null) {
            throw BusinessException.notFound("Datasource not found: " + id);
        }
        return config;
    }

    public DataSourceVo get(Long id) {
        AppUserPrincipal user = currentUser.required();
        return toVo(getConfigForQuery(id), user);
    }

    public DataSourceVo create(DataSourceReq req) {
        requireAdmin();
        DataSourceConfig config = new DataSourceConfig();
        applyReq(config, req);
        config.setPassword(cipher.encrypt(req.getPassword()));
        ConnectionTestResult verification = connectionManager.testConnection(config);
        config.setVerifiedReadOnly(verification.readOnlyVerified() ? 1 : 0);
        config.setLastVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        config.setCreatedBy(currentUser.required().userId());
        mapper.insert(config);
        audit.record("DATASOURCE_CREATE", "DATASOURCE", config.getId(), "SUCCESS",
                Map.of("dbType", config.getDbType(), "readOnlyVerified", true));
        return toVo(config, currentUser.required());
    }

    public DataSourceVo update(Long id, DataSourceReq req) {
        DataSourceConfig config = getConfigForAdmin(id);
        applyReq(config, req);
        // Blank password on update keeps the stored one.
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            config.setPassword(cipher.encrypt(req.getPassword()));
        }
        ConnectionTestResult verification = connectionManager.testConnection(config);
        config.setVerifiedReadOnly(verification.readOnlyVerified() ? 1 : 0);
        config.setLastVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.updateById(config);
        connectionManager.evict(id);
        schemaInspector.evict(id);
        audit.record("DATASOURCE_UPDATE", "DATASOURCE", id, "SUCCESS",
                Map.of("dbType", config.getDbType(), "readOnlyVerified", true));
        return toVo(config, currentUser.required());
    }

    public void delete(Long id) {
        getConfigForAdmin(id);
        mapper.deleteById(id);
        connectionManager.evict(id);
        schemaInspector.evict(id);
        audit.record("DATASOURCE_DELETE", "DATASOURCE", id, "SUCCESS", Map.of());
    }

    /** Test an unsaved datasource form. */
    public ConnectionTestResult testConnection(DataSourceReq req) {
        requireAdmin();
        DataSourceConfig config = new DataSourceConfig();
        applyReq(config, req);
        config.setPassword(req.getPassword()); // plaintext; decrypt() passes it through
        ConnectionTestResult result = connectionManager.testConnection(config);
        audit.record("DATASOURCE_TEST", "DATASOURCE", "unsaved", "SUCCESS",
                Map.of("dbType", config.getDbType(), "readOnlyVerified", result.readOnlyVerified()));
        return result;
    }

    public ConnectionTestResult testConnectionById(Long id) {
        DataSourceConfig config = getConfigForAdmin(id);
        ConnectionTestResult result = connectionManager.testConnection(config);
        config.setVerifiedReadOnly(result.readOnlyVerified() ? 1 : 0);
        config.setLastVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.updateById(config);
        audit.record("DATASOURCE_TEST", "DATASOURCE", id, "SUCCESS",
                Map.of("readOnlyVerified", result.readOnlyVerified()));
        return result;
    }

    /** Raw + semantic-enriched schema for a datasource. */
    public SchemaInfo getSchema(Long id, boolean refresh) {
        DataSourceConfig config = getConfigForQuery(id);
        SchemaInfo info = schemaInspector.inspect(config, refresh);
        semanticService.applyTo(info);
        return accessPolicy.filterSchema(currentUser.required(), id, info);
    }

    public SchemaInfo getSchemaForAdmin(Long id, boolean refresh) {
        DataSourceConfig config = getConfigForAdmin(id);
        SchemaInfo info = schemaInspector.inspect(config, refresh);
        semanticService.applyTo(info);
        return info;
    }

    /** Full server-side schema used only to resolve SQL resources before access-policy checks. */
    public SchemaInfo getSchemaForAuthorization(Long id) {
        DataSourceConfig config = getConfigForQuery(id);
        SchemaInfo info = schemaInspector.inspect(config, false);
        semanticService.applyTo(info);
        return info;
    }

    private void applyReq(DataSourceConfig config, DataSourceReq req) {
        config.setName(req.getName());
        config.setDbType(req.getDbType().toLowerCase());
        config.setHost(req.getHost());
        config.setPort(req.getPort());
        config.setDatabaseName(req.getDatabaseName());
        config.setUsername(req.getUsername());
        config.setJdbcParams(req.getJdbcParams() == null ? "" : req.getJdbcParams().trim());
        config.setRemark(req.getRemark());
    }

    private DataSourceConfig requireVerified(DataSourceConfig config) {
        if (!Integer.valueOf(1).equals(config.getVerifiedReadOnly())) {
            throw new BusinessException("Datasource has not passed read-only account verification");
        }
        return config;
    }

    private DataSourceVo toVo(DataSourceConfig config, AppUserPrincipal user) {
        DataSourceVo vo = DataSourceVo.from(config);
        vo.setCanQuery(accessPolicy.hasDatasourceCapability(user, config.getId(), Capability.QUERY));
        vo.setCanExport(accessPolicy.hasDatasourceCapability(user, config.getId(), Capability.EXPORT));
        vo.setCanManageSemantic(accessPolicy.hasDatasourceCapability(user, config.getId(), Capability.MANAGE_SEMANTIC));
        return vo;
    }

    private void requireAdmin() {
        if (!currentUser.required().isAdmin()) {
            throw BusinessException.forbidden("Administrator permission required");
        }
    }
}
