package com.chatbi.copilot.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.common.RequestIdFilter;
import com.chatbi.copilot.history.dto.QuerySnapshot;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.mapper.QueryHistoryMapper;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.text2sql.service.ValidatedQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HistoryService {
    private final QueryHistoryMapper mapper;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public HistoryService(QueryHistoryMapper mapper, CurrentUserService currentUser,
                          DataAccessPolicy accessPolicy, ObjectMapper objectMapper, AuditService audit) {
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public QueryHistory record(QueryResult result, String status, String error,
                               ValidatedQuery validated, String editedSql) {
        AppUserPrincipal user = currentUser.required();
        QueryHistory history = new QueryHistory();
        history.setUserId(user.userId());
        history.setSessionId(result.getSessionId());
        history.setDatasourceId(result.getDatasourceId());
        history.setQuestion(result.getQuestion());
        history.setGeneratedSql(result.getSql());
        history.setEditedSql(editedSql);
        history.setStatus(status);
        history.setSuccess("SUCCEEDED".equals(status) ? 1 : 0);
        history.setErrorMsg(limit(error, 1000));
        history.setExplanation(limit(result.getExplanation(), 2000));
        history.setClarification(limit(result.getClarification(), 1000));
        history.setRowCount(result.getRowCount());
        history.setElapsedMs(result.getElapsedMs());
        history.setChartType(result.getChart() == null ? null : result.getChart().getType());
        history.setRiskLevel(result.getRisk() == null ? null : result.getRisk().level());
        history.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
        if (validated != null) {
            history.setReferencedResources(toJson(Map.of(
                    "tables", validated.tables(), "columns", validated.columns(),
                    "functions", validated.functions())));
        }
        if (result.getSummary() != null) {
            history.setSummary(toJson(result.getSummary()));
        }
        if ("SUCCEEDED".equals(status) || result.getRisk() != null) {
            QuerySnapshot snapshot = new QuerySnapshot(result.getColumns(), result.getRows(),
                    result.getRowCount(), result.isTruncated(), result.getElapsedMs(),
                    result.getChart(), result.getSummary(), result.getRisk());
            String snapshotJson = toJson(snapshot);
            history.setResultSnapshot(snapshotJson);
            history.setResultHash(com.chatbi.copilot.auth.service.TokenHasher.sha256(snapshotJson));
        }
        mapper.insert(history);
        result.setQueryId(history.getId());
        audit.record("QUERY_" + status, "QUERY", history.getId(),
                switch (status) {
                    case "SUCCEEDED", "PREVIEWED", "CLARIFICATION", "NEEDS_CONFIRMATION" -> "SUCCESS";
                    case "BLOCKED" -> "DENIED";
                    default -> "FAILURE";
                },
                Map.of("question", safe(result.getQuestion()),
                        "sql", safe(result.getSql()),
                        "editedSql", safe(editedSql),
                        "rowCount", result.getRowCount(),
                        "resultHash", safe(history.getResultHash())));
        return history;
    }

    public IPage<QueryHistory> page(Long datasourceId, long page, long size) {
        AppUserPrincipal user = currentUser.required();
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<QueryHistory> wrapper = new LambdaQueryWrapper<QueryHistory>()
                .eq(QueryHistory::getUserId, user.userId())
                .eq(datasourceId != null, QueryHistory::getDatasourceId, datasourceId)
                .orderByDesc(QueryHistory::getId);
        if (datasourceId != null) {
            accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        } else if (!user.isAdmin()) {
            Set<Long> ids = accessPolicy.accessibleDatasourceIds(user, Capability.QUERY);
            if (ids.isEmpty()) {
                return new Page<>(safePage, safeSize, 0);
            }
            wrapper.in(QueryHistory::getDatasourceId, ids);
        }
        return mapper.selectPage(new Page<>(safePage, safeSize), wrapper);
    }

    public QueryHistory requiredOwned(Long id, Capability capability) {
        QueryHistory history = mapper.selectById(id);
        AppUserPrincipal user = currentUser.required();
        if (history == null || !user.userId().equals(history.getUserId())) {
            throw BusinessException.notFound("Query history not found");
        }
        accessPolicy.requireDatasource(user, history.getDatasourceId(), capability);
        return history;
    }

    public QuerySnapshot snapshot(Long id, Capability capability) {
        QueryHistory history = requiredOwned(id, capability);
        if (!"SUCCEEDED".equals(history.getStatus()) || history.getResultSnapshot() == null) {
            throw new BusinessException("This query has no exportable result snapshot");
        }
        return decodeSnapshot(history);
    }

    private QuerySnapshot decodeSnapshot(QueryHistory history) {
        String actualHash = com.chatbi.copilot.auth.service.TokenHasher.sha256(history.getResultSnapshot());
        if (history.getResultHash() == null || !history.getResultHash().equals(actualHash)) {
            throw new IllegalStateException("Stored query snapshot failed integrity validation");
        }
        try {
            return objectMapper.readValue(history.getResultSnapshot(), QuerySnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored query snapshot is invalid", e);
        }
    }

    public QueryResult result(Long id, Capability capability) {
        QueryHistory history = requiredOwned(id, capability);
        QueryResult result = new QueryResult();
        result.setQueryId(history.getId());
        result.setSessionId(history.getSessionId());
        result.setDatasourceId(history.getDatasourceId());
        result.setQuestion(history.getQuestion());
        result.setSql(history.getGeneratedSql());
        result.setExplanation(history.getExplanation());
        result.setClarification(history.getClarification());
        result.setNeedClarification("CLARIFICATION".equals(history.getStatus()));
        result.setRequiresConfirmation("NEEDS_CONFIRMATION".equals(history.getStatus()));
        if ("SUCCEEDED".equals(history.getStatus())) {
            QuerySnapshot snapshot = snapshot(id, capability);
            result.setColumns(snapshot.columns());
            result.setRows(snapshot.rows());
            result.setRowCount(snapshot.rowCount());
            result.setTruncated(snapshot.truncated());
            result.setElapsedMs(snapshot.elapsedMs());
            result.setChart(snapshot.chart());
            result.setSummary(snapshot.summary());
            result.setRisk(snapshot.risk());
        } else if (history.getResultSnapshot() != null) {
            result.setRisk(decodeSnapshot(history).risk());
        } else if (history.getSummary() != null) {
            try {
                result.setSummary(objectMapper.readValue(history.getSummary(),
                        com.chatbi.copilot.text2sql.dto.ResultSummary.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored query summary is invalid", e);
            }
        }
        return result;
    }

    @Transactional
    public void delete(Long id) {
        QueryHistory history = requiredOwned(id, Capability.QUERY);
        mapper.deleteById(history.getId());
        audit.record("HISTORY_DELETE", "QUERY", id, "SUCCESS", Map.of());
    }

    @Transactional
    public void clear(Long datasourceId) {
        AppUserPrincipal user = currentUser.required();
        if (datasourceId == null) {
            throw new BusinessException("datasourceId is required when clearing history");
        }
        accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        mapper.delete(new LambdaQueryWrapper<QueryHistory>()
                .eq(QueryHistory::getUserId, user.userId())
                .eq(QueryHistory::getDatasourceId, datasourceId));
        audit.record("HISTORY_CLEAR", "DATASOURCE", datasourceId, "SUCCESS", Map.of());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Query history could not be serialized", e);
        }
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
