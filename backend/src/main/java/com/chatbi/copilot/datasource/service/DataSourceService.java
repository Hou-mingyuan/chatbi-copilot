package com.chatbi.copilot.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.dto.DataSourceReq;
import com.chatbi.copilot.datasource.dto.DataSourceVo;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.semantic.service.SemanticService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataSourceService {

    private final DataSourceConfigMapper mapper;
    private final DynamicConnectionManager connectionManager;
    private final SchemaInspector schemaInspector;
    private final SemanticService semanticService;
    private final PasswordCipher cipher;

    public DataSourceService(DataSourceConfigMapper mapper,
                             DynamicConnectionManager connectionManager,
                             SchemaInspector schemaInspector,
                             SemanticService semanticService,
                             PasswordCipher cipher) {
        this.mapper = mapper;
        this.connectionManager = connectionManager;
        this.schemaInspector = schemaInspector;
        this.semanticService = semanticService;
        this.cipher = cipher;
    }

    public List<DataSourceVo> list() {
        LambdaQueryWrapper<DataSourceConfig> wrapper = new LambdaQueryWrapper<DataSourceConfig>()
                .orderByDesc(DataSourceConfig::getId);
        return mapper.selectList(wrapper).stream().map(DataSourceVo::from).toList();
    }

    /** Internal: fetch the full config (with encrypted password) or throw. */
    public DataSourceConfig getConfig(Long id) {
        DataSourceConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BusinessException("Datasource not found: " + id);
        }
        return config;
    }

    public DataSourceVo get(Long id) {
        return DataSourceVo.from(getConfig(id));
    }

    public DataSourceVo create(DataSourceReq req) {
        DataSourceConfig config = new DataSourceConfig();
        applyReq(config, req);
        config.setPassword(cipher.encrypt(req.getPassword()));
        mapper.insert(config);
        return DataSourceVo.from(config);
    }

    public DataSourceVo update(Long id, DataSourceReq req) {
        DataSourceConfig config = getConfig(id);
        applyReq(config, req);
        // Blank password on update keeps the stored one.
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            config.setPassword(cipher.encrypt(req.getPassword()));
        }
        mapper.updateById(config);
        connectionManager.evict(id);
        schemaInspector.evict(id);
        return DataSourceVo.from(config);
    }

    public void delete(Long id) {
        getConfig(id);
        mapper.deleteById(id);
        connectionManager.evict(id);
        schemaInspector.evict(id);
    }

    /** Test an unsaved datasource form. */
    public void testConnection(DataSourceReq req) {
        DataSourceConfig config = new DataSourceConfig();
        applyReq(config, req);
        config.setPassword(req.getPassword()); // plaintext; decrypt() passes it through
        connectionManager.testConnection(config);
    }

    public void testConnectionById(Long id) {
        connectionManager.testConnection(getConfig(id));
    }

    /** Raw + semantic-enriched schema for a datasource. */
    public SchemaInfo getSchema(Long id, boolean refresh) {
        DataSourceConfig config = getConfig(id);
        SchemaInfo info = schemaInspector.inspect(config, refresh);
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
        config.setJdbcParams(req.getJdbcParams());
        config.setRemark(req.getRemark());
    }
}
