package com.chatbi.copilot.semantic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.datasource.service.SchemaInspector;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import com.chatbi.copilot.semantic.SemanticDefinitionType;
import com.chatbi.copilot.semantic.dto.SemanticModelReq;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.entity.SemanticRevision;
import com.chatbi.copilot.semantic.mapper.SemanticModelMapper;
import com.chatbi.copilot.semantic.mapper.SemanticRevisionMapper;
import com.chatbi.copilot.text2sql.service.RawColumnReference;
import com.chatbi.copilot.text2sql.service.SqlGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SemanticService {
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX", "COUNT_DISTINCT");
    private static final Set<String> TIME_GRAINS = Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");
    private static final Set<String> JOIN_TYPES = Set.of("INNER", "LEFT");
    private static final Pattern UNSAFE_EXPRESSION = Pattern.compile("(?i)(;|--|/\\*|\\b(insert|update|delete|drop|alter|create|grant|revoke|copy)\\b)");

    private final SemanticModelMapper mapper;
    private final SemanticRevisionMapper revisionMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;
    private final AuditService audit;
    private final DataSourceConfigMapper datasourceMapper;
    private final SchemaInspector schemaInspector;
    private final SqlGuard sqlGuard;

    public SemanticService(SemanticModelMapper mapper, SemanticRevisionMapper revisionMapper,
                           ObjectMapper objectMapper, CurrentUserService currentUser,
                           DataAccessPolicy accessPolicy, AuditService audit,
                           DataSourceConfigMapper datasourceMapper, SchemaInspector schemaInspector,
                           SqlGuard sqlGuard) {
        this.mapper = mapper;
        this.revisionMapper = revisionMapper;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.datasourceMapper = datasourceMapper;
        this.schemaInspector = schemaInspector;
        this.sqlGuard = sqlGuard;
    }

    public List<SemanticModel> list(Long datasourceId) {
        accessPolicy.requireDatasource(currentUser.required(), datasourceId, Capability.QUERY);
        return listUnchecked(datasourceId, false);
    }

    public List<SemanticModel> listActiveUnchecked(Long datasourceId) {
        return listUnchecked(datasourceId, true);
    }

    private List<SemanticModel> listUnchecked(Long datasourceId, boolean activeOnly) {
        LambdaQueryWrapper<SemanticModel> wrapper = new LambdaQueryWrapper<SemanticModel>()
                .eq(SemanticModel::getDatasourceId, datasourceId)
                .eq(activeOnly, SemanticModel::getActive, 1)
                .orderByAsc(SemanticModel::getDefinitionType)
                .orderByAsc(SemanticModel::getTableName)
                .orderByAsc(SemanticModel::getColumnName)
                .orderByAsc(SemanticModel::getId);
        return mapper.selectList(wrapper);
    }

    @Transactional
    public SemanticModel create(SemanticModelReq req) {
        accessPolicy.requireDatasource(currentUser.required(), req.getDatasourceId(), Capability.MANAGE_SEMANTIC);
        SemanticDefinitionType type = validate(req);
        validateResources(req, type);
        ensureUnique(null, req, type);
        SemanticModel model = new SemanticModel();
        apply(model, req, type);
        model.setVersion(1);
        model.setCreatedBy(currentUser.required().userId());
        model.setUpdatedBy(currentUser.required().userId());
        mapper.insert(model);
        revision(model, "CREATE");
        audit.record("SEMANTIC_CREATE", "SEMANTIC", model.getId(), "SUCCESS",
                Map.of("datasourceId", model.getDatasourceId(), "type", model.getDefinitionType()));
        return model;
    }

    @Transactional
    public SemanticModel update(Long id, SemanticModelReq req) {
        SemanticModel model = required(id);
        if (!model.getDatasourceId().equals(req.getDatasourceId())) {
            throw new BusinessException("A semantic definition cannot move to another datasource");
        }
        accessPolicy.requireDatasource(currentUser.required(), model.getDatasourceId(), Capability.MANAGE_SEMANTIC);
        SemanticDefinitionType type = validate(req);
        validateResources(req, type);
        ensureUnique(id, req, type);
        apply(model, req, type);
        model.setVersion((model.getVersion() == null ? 0 : model.getVersion()) + 1);
        model.setUpdatedBy(currentUser.required().userId());
        mapper.updateById(model);
        revision(model, "UPDATE");
        audit.record("SEMANTIC_UPDATE", "SEMANTIC", id, "SUCCESS",
                Map.of("version", model.getVersion(), "type", model.getDefinitionType()));
        return model;
    }

    @Transactional
    public void delete(Long id) {
        SemanticModel model = required(id);
        accessPolicy.requireDatasource(currentUser.required(), model.getDatasourceId(), Capability.MANAGE_SEMANTIC);
        model.setVersion((model.getVersion() == null ? 0 : model.getVersion()) + 1);
        revision(model, "DELETE");
        mapper.deleteById(id);
        audit.record("SEMANTIC_DELETE", "SEMANTIC", id, "SUCCESS",
                Map.of("datasourceId", model.getDatasourceId()));
    }

    public List<SemanticRevision> revisions(Long id) {
        SemanticModel model = required(id);
        accessPolicy.requireDatasource(currentUser.required(), model.getDatasourceId(), Capability.QUERY);
        return revisionMapper.selectList(new LambdaQueryWrapper<SemanticRevision>()
                .eq(SemanticRevision::getSemanticId, id)
                .orderByDesc(SemanticRevision::getVersion));
    }

    public void applyTo(SchemaInfo info) {
        if (info == null || info.getDatasourceId() == null) {
            return;
        }
        List<SemanticModel> models = listActiveUnchecked(info.getDatasourceId());
        Map<String, SemanticModel> tableMap = new HashMap<>();
        Map<String, SemanticModel> columnMap = new HashMap<>();
        for (SemanticModel model : models) {
            SemanticDefinitionType type = typeOf(model.getDefinitionType());
            if (type == SemanticDefinitionType.TABLE) {
                tableMap.put(normalize(model.getTableName()), model);
            } else if (type == SemanticDefinitionType.COLUMN) {
                columnMap.put(normalize(model.getTableName()) + "." + normalize(model.getColumnName()), model);
            }
        }
        for (TableSchema table : info.getTables()) {
            SemanticModel tableModel = tableMap.get(normalize(table.getName()));
            if (tableModel != null) {
                table.setBusinessAlias(tableModel.getBusinessAlias());
                table.setBusinessDescription(tableModel.getDescription());
            }
            for (ColumnSchema column : table.getColumns()) {
                SemanticModel columnModel = columnMap.get(normalize(table.getName()) + "." + normalize(column.getName()));
                if (columnModel != null) {
                    column.setBusinessAlias(columnModel.getBusinessAlias());
                    column.setBusinessDescription(columnModel.getDescription());
                }
            }
        }
    }

    private SemanticDefinitionType validate(SemanticModelReq req) {
        SemanticDefinitionType type = typeOf(req.getDefinitionType());
        requireText(req.getTableName(), "tableName");
        switch (type) {
            case TABLE -> requireAny(req.getBusinessAlias(), req.getDescription(), "Table alias or description is required");
            case COLUMN -> {
                requireText(req.getColumnName(), "columnName");
                requireAny(req.getBusinessAlias(), req.getDescription(), "Column alias or description is required");
            }
            case METRIC -> {
                requireText(req.getBusinessAlias(), "businessAlias");
                requireText(req.getMetricExpression(), "metricExpression");
                requireChoice(req.getAggregation(), AGGREGATIONS, "aggregation");
                if (UNSAFE_EXPRESSION.matcher(req.getMetricExpression()).find()) {
                    throw new BusinessException("Metric expression contains an unsafe SQL construct");
                }
            }
            case ENUM -> {
                requireText(req.getColumnName(), "columnName");
                requireText(req.getEnumValue(), "enumValue");
                requireText(req.getEnumLabel(), "enumLabel");
            }
            case TIME -> {
                requireText(req.getColumnName(), "columnName");
                requireChoice(req.getTimeGrain(), TIME_GRAINS, "timeGrain");
            }
            case JOIN -> {
                requireText(req.getColumnName(), "columnName");
                requireText(req.getRelatedTable(), "relatedTable");
                requireText(req.getRelatedColumn(), "relatedColumn");
                requireChoice(req.getJoinType(), JOIN_TYPES, "joinType");
            }
        }
        return type;
    }

    private void ensureUnique(Long ignoredId, SemanticModelReq req, SemanticDefinitionType type) {
        LambdaQueryWrapper<SemanticModel> wrapper = new LambdaQueryWrapper<SemanticModel>()
                .eq(SemanticModel::getDatasourceId, req.getDatasourceId())
                .eq(SemanticModel::getDefinitionType, type.name())
                .eq(SemanticModel::getTableName, normalize(req.getTableName()))
                .eq(req.getColumnName() != null && !req.getColumnName().isBlank(),
                        SemanticModel::getColumnName, normalize(req.getColumnName()))
                .ne(ignoredId != null, SemanticModel::getId, ignoredId);
        Long count = mapper.selectCount(wrapper);
        if (count != null && count > 0 && type != SemanticDefinitionType.ENUM) {
            throw new BusinessException("A semantic definition for this resource and type already exists");
        }
    }

    private void validateResources(SemanticModelReq req, SemanticDefinitionType type) {
        DataSourceConfig config = datasourceMapper.selectById(req.getDatasourceId());
        if (config == null) {
            throw BusinessException.notFound("Datasource not found");
        }
        if (!Integer.valueOf(1).equals(config.getVerifiedReadOnly())) {
            throw new BusinessException("Datasource must pass read-only verification before semantic editing");
        }
        SchemaInfo schema = schemaInspector.inspect(config, false);
        SchemaIndex index = new SchemaIndex(schema);
        String table = normalize(req.getTableName());
        if (!index.tables.containsKey(table)) {
            throw new BusinessException("tableName does not exist in the datasource schema");
        }
        if (type == SemanticDefinitionType.COLUMN || type == SemanticDefinitionType.ENUM
                || type == SemanticDefinitionType.TIME || type == SemanticDefinitionType.JOIN) {
            requireExistingColumn(index, table, req.getColumnName(), "columnName");
        }
        if (type == SemanticDefinitionType.METRIC) {
            if (req.getColumnName() != null && !req.getColumnName().isBlank()) {
                requireExistingColumn(index, table, req.getColumnName(), "columnName");
            }
            validateMetricExpression(index, table, req.getMetricExpression());
        }
        if (type == SemanticDefinitionType.TIME) {
            String dataType = index.columns.get(table).get(normalize(req.getColumnName()));
            String upper = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
            if (!upper.contains("DATE") && !upper.contains("TIME") && !upper.contains("YEAR")) {
                throw new BusinessException("TIME definition must reference a date/time column");
            }
        }
        if (type == SemanticDefinitionType.JOIN) {
            String relatedTable = normalize(req.getRelatedTable());
            if (!index.tables.containsKey(relatedTable)) {
                throw new BusinessException("relatedTable does not exist in the datasource schema");
            }
            requireExistingColumn(index, relatedTable, req.getRelatedColumn(), "relatedColumn");
        }
    }

    private void validateMetricExpression(SchemaIndex index, String table, String expression) {
        var inspection = sqlGuard.inspect("SELECT " + expression + " FROM " + table + " LIMIT 1");
        if (!inspection.tables().equals(Set.of(table))) {
            throw new BusinessException("Metric expression may reference only its declared table");
        }
        for (RawColumnReference raw : inspection.columns()) {
            String qualifier = normalize(raw.qualifier());
            if (qualifier != null && !qualifier.isBlank() && !qualifier.equals(table)) {
                throw new BusinessException("Metric expression contains an unknown table qualifier");
            }
            if (!index.columns.get(table).containsKey(normalize(raw.column()))) {
                throw new BusinessException("Metric expression references a column that does not exist");
            }
        }
    }

    private void requireExistingColumn(SchemaIndex index, String table, String column, String field) {
        if (!index.columns.getOrDefault(table, Map.of()).containsKey(normalize(column))) {
            throw new BusinessException(field + " does not exist in the datasource schema");
        }
    }

    private static class SchemaIndex {
        private final Map<String, TableSchema> tables = new HashMap<>();
        private final Map<String, Map<String, String>> columns = new HashMap<>();

        private SchemaIndex(SchemaInfo schema) {
            for (TableSchema table : schema.getTables()) {
                String tableName = table.getName().trim().toLowerCase(Locale.ROOT);
                tables.put(tableName, table);
                Map<String, String> tableColumns = new HashMap<>();
                for (ColumnSchema column : table.getColumns()) {
                    tableColumns.put(column.getName().trim().toLowerCase(Locale.ROOT), column.getDataType());
                }
                columns.put(tableName, Map.copyOf(tableColumns));
            }
        }
    }

    private void apply(SemanticModel model, SemanticModelReq req, SemanticDefinitionType type) {
        model.setDatasourceId(req.getDatasourceId());
        model.setDefinitionType(type.name());
        model.setTableName(normalize(req.getTableName()));
        model.setColumnName(blankToNull(req.getColumnName()));
        model.setBusinessAlias(cleanText(req.getBusinessAlias()));
        model.setDescription(cleanText(req.getDescription()));
        model.setMetricExpression(cleanText(req.getMetricExpression()));
        model.setAggregation(upperOrNull(req.getAggregation()));
        model.setUnit(cleanText(req.getUnit()));
        model.setTimeGrain(upperOrNull(req.getTimeGrain()));
        model.setEnumValue(cleanText(req.getEnumValue()));
        model.setEnumLabel(cleanText(req.getEnumLabel()));
        model.setRelatedTable(blankToNull(req.getRelatedTable()));
        model.setRelatedColumn(blankToNull(req.getRelatedColumn()));
        model.setJoinType(upperOrNull(req.getJoinType()));
        model.setActive(req.isActive() ? 1 : 0);
    }

    private SemanticModel required(Long id) {
        SemanticModel model = mapper.selectById(id);
        if (model == null) {
            throw BusinessException.notFound("Semantic definition not found");
        }
        return model;
    }

    private void revision(SemanticModel model, String changeType) {
        SemanticRevision revision = new SemanticRevision();
        revision.setSemanticId(model.getId());
        revision.setDatasourceId(model.getDatasourceId());
        revision.setVersion(model.getVersion());
        revision.setDefinitionType(model.getDefinitionType());
        revision.setSnapshot(toJson(model));
        revision.setChangedBy(currentUser.required().userId());
        revision.setChangeType(changeType);
        revisionMapper.insert(revision);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Semantic revision could not be serialized", e);
        }
    }

    private SemanticDefinitionType typeOf(String value) {
        try {
            return SemanticDefinitionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new BusinessException("definitionType must be TABLE, COLUMN, METRIC, ENUM, TIME, or JOIN");
        }
    }

    private void requireChoice(String value, Set<String> choices, String field) {
        String normalized = upperOrNull(value);
        if (normalized == null || !choices.contains(normalized)) {
            throw new BusinessException(field + " must be one of " + choices);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required for this semantic type");
        }
    }

    private void requireAny(String first, String second, String message) {
        if ((first == null || first.isBlank()) && (second == null || second.isBlank())) {
            throw new BusinessException(message);
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\u0000', ' ').trim();
    }

    private String blankToNull(String value) {
        String cleaned = cleanText(value);
        return cleaned == null || cleaned.isBlank() ? null : normalize(cleaned);
    }

    private String upperOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
