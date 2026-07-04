package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.chart.ChartRecommendation;
import com.chatbi.copilot.chart.ChartRecommender;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DataSourceService;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.llm.LlmClient;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.LlmSqlAnswer;
import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Orchestrates the full Text2SQL pipeline: schema-aware prompt -&gt; LLM -&gt; safety guard -&gt;
 * execution -&gt; chart recommendation -&gt; history.
 */
@Slf4j
@Service
public class Text2SqlService {

    private final DataSourceService dataSourceService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final SqlGuard sqlGuard;
    private final SqlExecutor sqlExecutor;
    private final ChartRecommender chartRecommender;
    private final HistoryService historyService;
    private final SqlGuardProperties guardProperties;
    private final ObjectMapper objectMapper;

    public Text2SqlService(DataSourceService dataSourceService,
                           PromptBuilder promptBuilder,
                           LlmClient llmClient,
                           SqlGuard sqlGuard,
                           SqlExecutor sqlExecutor,
                           ChartRecommender chartRecommender,
                           HistoryService historyService,
                           SqlGuardProperties guardProperties,
                           ObjectMapper objectMapper) {
        this.dataSourceService = dataSourceService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.sqlGuard = sqlGuard;
        this.sqlExecutor = sqlExecutor;
        this.chartRecommender = chartRecommender;
        this.historyService = historyService;
        this.guardProperties = guardProperties;
        this.objectMapper = objectMapper;
    }

    /** Natural-language question -> generated SQL -> executed result + chart. */
    public QueryResult ask(AskRequest req) {
        DataSourceConfig config = dataSourceService.getConfig(req.getDatasourceId());
        SchemaInfo schema = dataSourceService.getSchema(req.getDatasourceId(), false);

        QueryResult result = new QueryResult();
        result.setDatasourceId(req.getDatasourceId());
        result.setQuestion(req.getQuestion());

        List<ChatMessage> messages = promptBuilder.buildMessages(schema, req.getQuestion(), req.getHistory());
        String raw = llmClient.chat(messages);
        LlmSqlAnswer answer = parseAnswer(raw);

        if (answer.isNeedClarification()) {
            result.setNeedClarification(true);
            result.setClarification(StringUtils.hasText(answer.getClarification())
                    ? answer.getClarification() : "The question is ambiguous. Please add more detail.");
            result.setExplanation(answer.getExplanation());
            return result;
        }
        if (!StringUtils.hasText(answer.getSql())) {
            throw new BusinessException("The model did not return any SQL. Try rephrasing the question.");
        }

        String safeSql = sqlGuard.sanitize(answer.getSql());
        result.setSql(safeSql);
        result.setExplanation(answer.getExplanation());

        if (req.isPreviewOnly()) {
            historyService.record(req.getDatasourceId(), req.getQuestion(), safeSql, true, null, null, null, null);
            return result;
        }

        try {
            QueryExecResult exec = sqlExecutor.execute(config, safeSql, guardProperties.getMaxLimit());
            ChartRecommendation chart = chartRecommender.recommend(exec.getColumns(), exec.getRows());
            fill(result, exec, chart);
            historyService.record(req.getDatasourceId(), req.getQuestion(), safeSql, true,
                    null, exec.getRowCount(), exec.getElapsedMs(), chart.getType());
            return result;
        } catch (BusinessException e) {
            historyService.record(req.getDatasourceId(), req.getQuestion(), safeSql, false,
                    e.getMessage(), null, null, null);
            throw e;
        }
    }

    /** Execute a user-provided/edited SQL (guarded), e.g. re-running from history. */
    public QueryResult run(Long datasourceId, String sql) {
        DataSourceConfig config = dataSourceService.getConfig(datasourceId);
        String safeSql = sqlGuard.sanitize(sql);

        QueryResult result = new QueryResult();
        result.setDatasourceId(datasourceId);
        result.setQuestion("(manual SQL)");
        result.setSql(safeSql);

        try {
            QueryExecResult exec = sqlExecutor.execute(config, safeSql, guardProperties.getMaxLimit());
            ChartRecommendation chart = chartRecommender.recommend(exec.getColumns(), exec.getRows());
            fill(result, exec, chart);
            historyService.record(datasourceId, "(manual SQL)", safeSql, true,
                    null, exec.getRowCount(), exec.getElapsedMs(), chart.getType());
            return result;
        } catch (BusinessException e) {
            historyService.record(datasourceId, "(manual SQL)", safeSql, false, e.getMessage(), null, null, null);
            throw e;
        }
    }

    /** Guarded execution used by the Excel export endpoint. */
    public QueryExecResult executeForExport(Long datasourceId, String sql) {
        DataSourceConfig config = dataSourceService.getConfig(datasourceId);
        String safeSql = sqlGuard.sanitize(sql);
        return sqlExecutor.execute(config, safeSql, guardProperties.getMaxLimit());
    }

    private void fill(QueryResult result, QueryExecResult exec, ChartRecommendation chart) {
        result.setColumns(exec.getColumns());
        result.setRows(exec.getRows());
        result.setRowCount(exec.getRowCount());
        result.setTruncated(exec.isTruncated());
        result.setElapsedMs(exec.getElapsedMs());
        result.setChart(chart);
    }

    private LlmSqlAnswer parseAnswer(String raw) {
        String json = extractJson(raw);
        if (json != null) {
            try {
                return objectMapper.readValue(json, LlmSqlAnswer.class);
            } catch (Exception e) {
                log.warn("Failed to parse LLM JSON, falling back to raw SQL: {}", e.getMessage());
            }
        }
        LlmSqlAnswer fallback = new LlmSqlAnswer();
        fallback.setSql(raw);
        return fallback;
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }
}
