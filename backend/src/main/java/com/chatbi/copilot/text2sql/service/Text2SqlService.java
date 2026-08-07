package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.chart.ChartRecommendation;
import com.chatbi.copilot.chart.ChartRecommender;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.service.DataSourceService;
import com.chatbi.copilot.history.entity.QuerySession;
import com.chatbi.copilot.history.service.ConversationService;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.llm.LlmClient;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.LlmSqlAnswer;
import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.semantic.service.SemanticContext;
import com.chatbi.copilot.semantic.service.SemanticContextRetriever;
import com.chatbi.copilot.text2sql.plan.QueryPlanService;
import com.chatbi.copilot.text2sql.plan.QueryRisk;
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
    private final SecureQueryValidator secureQueryValidator;
    private final SqlExecutor sqlExecutor;
    private final ChartRecommender chartRecommender;
    private final HistoryService historyService;
    private final SqlGuardProperties guardProperties;
    private final ObjectMapper objectMapper;
    private final SemanticContextRetriever contextRetriever;
    private final QueryPlanService queryPlanService;
    private final ConversationService conversationService;
    private final ResultInterpreter resultInterpreter;
    private final ClarificationPolicy clarificationPolicy;

    public Text2SqlService(DataSourceService dataSourceService,
                           PromptBuilder promptBuilder,
                           LlmClient llmClient,
                           SecureQueryValidator secureQueryValidator,
                           SqlExecutor sqlExecutor,
                           ChartRecommender chartRecommender,
                           HistoryService historyService,
                           SqlGuardProperties guardProperties,
                           ObjectMapper objectMapper,
                           SemanticContextRetriever contextRetriever,
                           QueryPlanService queryPlanService,
                           ConversationService conversationService,
                           ResultInterpreter resultInterpreter,
                           ClarificationPolicy clarificationPolicy) {
        this.dataSourceService = dataSourceService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.secureQueryValidator = secureQueryValidator;
        this.sqlExecutor = sqlExecutor;
        this.chartRecommender = chartRecommender;
        this.historyService = historyService;
        this.guardProperties = guardProperties;
        this.objectMapper = objectMapper;
        this.contextRetriever = contextRetriever;
        this.queryPlanService = queryPlanService;
        this.conversationService = conversationService;
        this.resultInterpreter = resultInterpreter;
        this.clarificationPolicy = clarificationPolicy;
    }

    /** Natural-language question -> generated SQL -> executed result + chart. */
    public QueryResult ask(AskRequest req) {
        return ask(req, QueryProgress.NONE);
    }

    public QueryResult ask(AskRequest req, QueryProgress progress) {
        progress.stage("PREPARING", 5, "正在准备数据上下文");
        QueryResult result = new QueryResult();
        result.setDatasourceId(req.getDatasourceId());
        result.setQuestion(req.getQuestion());
        QuerySession session = conversationService.resolve(req.getDatasourceId(), req.getSessionId(), req.getQuestion());
        result.setSessionId(session.getId());
        ValidatedQuery validated = null;
        try {
            progress.checkCancelled();
            DataSourceConfig config = dataSourceService.getConfigForQuery(req.getDatasourceId());
            SchemaInfo schema = dataSourceService.getSchema(req.getDatasourceId(), false);
            List<HistoryTurn> history = conversationService.context(session.getId());
            SemanticContext context = contextRetriever.retrieve(schema, retrievalQuestion(req.getQuestion(), history));
            var deterministicClarification = clarificationPolicy.evaluate(req.getQuestion(), history);
            if (deterministicClarification.isPresent()) {
                result.setNeedClarification(true);
                result.setClarification(deterministicClarification.get().message());
                historyService.record(result, "CLARIFICATION", null, null, null);
                return result;
            }
            progress.stage("GENERATING_SQL", 25, "正在生成查询");
            List<ChatMessage> messages = promptBuilder.buildMessages(
                    context, req.getQuestion(), history);
            String raw = llmClient.chat(messages);
            LlmSqlAnswer answer = parseAnswer(raw);

            if (answer.isNeedClarification()) {
                result.setNeedClarification(true);
                result.setClarification(StringUtils.hasText(answer.getClarification())
                        ? answer.getClarification() : "请补充时间范围、指标口径或分析维度。");
                historyService.record(result, "CLARIFICATION", null, null, null);
                return result;
            }
            if (!StringUtils.hasText(answer.getSql())) {
                throw new BusinessException("模型未返回可执行 SQL，请补充问题细节后重试");
            }

            progress.stage("VALIDATING_SQL", 45, "正在校验 SQL 与数据权限");
            validated = secureQueryValidator.validate(answer.getSql(), req.getDatasourceId(),
                    com.chatbi.copilot.permission.Capability.QUERY,
                    dataSourceService.getSchemaForAuthorization(req.getDatasourceId()));
            String safeSql = validated.sql();
            result.setSql(safeSql);
            result.setExplanation(answer.getExplanation());

            progress.stage("ANALYZING_PLAN", 60, "正在评估执行计划");
            QueryRisk risk = queryPlanService.analyze(config, safeSql);
            result.setRisk(risk);
            if (risk.blocked()) {
                historyService.record(result, "BLOCKED", "Query scan exceeds the safety budget", validated, null);
                throw new BusinessException("查询预计扫描量超过安全上限，已阻止执行");
            }
            if (risk.requiresConfirmation() && !req.isConfirmRisk()) {
                result.setRequiresConfirmation(true);
                historyService.record(result, "NEEDS_CONFIRMATION", null, validated, null);
                return result;
            }

            if (req.isPreviewOnly()) {
                historyService.record(result, "PREVIEWED", null, validated, null);
                return result;
            }

            progress.stage("EXECUTING", 75, "正在执行只读查询");
            QueryExecResult exec = sqlExecutor.execute(config, safeSql, guardProperties.getMaxLimit());
            progress.stage("INTERPRETING", 90, "正在生成图表和数据解读");
            ChartRecommendation chart = chartRecommender.recommend(exec.getColumns(), exec.getRows());
            fill(result, exec, chart);
            result.setSummary(resultInterpreter.interpret(exec.getColumns(), exec.getRows(), exec.isTruncated()));
            result.setExplanation(result.getSummary().text());
            historyService.record(result, "SUCCEEDED", null, validated, null);
            return result;
        } catch (BusinessException e) {
            progress.checkCancelled();
            if (result.getQueryId() == null) {
                historyService.record(result, "FAILED", e.getMessage(), validated, null);
            }
            throw e;
        } catch (RuntimeException e) {
            progress.checkCancelled();
            if (result.getQueryId() == null) {
                historyService.record(result, "FAILED", "Query processing failed", validated, null);
            }
            throw e;
        }
    }

    /** Execute a user-provided/edited SQL (guarded), e.g. re-running from history. */
    public QueryResult run(Long datasourceId, String sql) {
        return run(datasourceId, sql, false, null, null);
    }

    public QueryResult run(Long datasourceId, String sql, boolean confirmRisk, String executionId) {
        return run(datasourceId, sql, confirmRisk, executionId, null);
    }

    public QueryResult run(Long datasourceId, String sql, boolean confirmRisk,
                           String executionId, String requestedSessionId) {
        return run(datasourceId, sql, confirmRisk, executionId, requestedSessionId, QueryProgress.NONE);
    }

    public QueryResult run(Long datasourceId, String sql, boolean confirmRisk,
                           String executionId, String requestedSessionId, QueryProgress progress) {
        progress.stage("PREPARING", 5, "正在准备手写 SQL 查询");
        QueryResult result = new QueryResult();
        result.setDatasourceId(datasourceId);
        result.setQuestion("(manual SQL)");
        QuerySession session = conversationService.resolve(datasourceId, requestedSessionId, result.getQuestion());
        result.setSessionId(session.getId());
        ValidatedQuery validated = null;
        try {
            progress.stage("VALIDATING_SQL", 35, "正在校验 SQL 与数据权限");
            DataSourceConfig config = dataSourceService.getConfigForQuery(datasourceId);
            SchemaInfo schema = dataSourceService.getSchemaForAuthorization(datasourceId);
            validated = secureQueryValidator.validate(sql, datasourceId,
                    com.chatbi.copilot.permission.Capability.QUERY, schema);
            String safeSql = validated.sql();
            result.setSql(safeSql);
            progress.stage("ANALYZING_PLAN", 55, "正在评估执行计划");
            QueryRisk risk = queryPlanService.analyze(config, safeSql);
            result.setRisk(risk);
            if (risk.blocked()) {
                historyService.record(result, "BLOCKED", "Query scan exceeds the safety budget", validated, safeSql);
                throw new BusinessException("查询预计扫描量超过安全上限，已阻止执行");
            }
            if (risk.requiresConfirmation() && !confirmRisk) {
                result.setRequiresConfirmation(true);
                historyService.record(result, "NEEDS_CONFIRMATION", null, validated, safeSql);
                return result;
            }
            progress.stage("EXECUTING", 75, "正在执行只读查询");
            QueryExecResult exec = sqlExecutor.execute(config, safeSql, guardProperties.getMaxLimit(), executionId);
            progress.stage("INTERPRETING", 90, "正在生成图表和数据解读");
            ChartRecommendation chart = chartRecommender.recommend(exec.getColumns(), exec.getRows());
            fill(result, exec, chart);
            result.setSummary(resultInterpreter.interpret(exec.getColumns(), exec.getRows(), exec.isTruncated()));
            result.setExplanation(result.getSummary().text());
            historyService.record(result, "SUCCEEDED", null, validated, safeSql);
            return result;
        } catch (BusinessException e) {
            progress.checkCancelled();
            if (result.getQueryId() == null) {
                historyService.record(result, "FAILED", e.getMessage(), validated, result.getSql());
            }
            throw e;
        } catch (RuntimeException e) {
            progress.checkCancelled();
            if (result.getQueryId() == null) {
                historyService.record(result, "FAILED", "Query processing failed", validated, result.getSql());
            }
            throw e;
        }
    }

    private void fill(QueryResult result, QueryExecResult exec, ChartRecommendation chart) {
        result.setColumns(exec.getColumns());
        result.setRows(exec.getRows());
        result.setRowCount(exec.getRowCount());
        result.setTruncated(exec.isTruncated());
        result.setElapsedMs(exec.getElapsedMs());
        result.setChart(chart);
    }

    private String retrievalQuestion(String currentQuestion, List<HistoryTurn> history) {
        StringBuilder context = new StringBuilder();
        if (history != null) {
            history.stream()
                    .map(HistoryTurn::getQuestion)
                    .filter(StringUtils::hasText)
                    .forEach(question -> context.append(question).append('\n'));
        }
        if (StringUtils.hasText(currentQuestion)) {
            context.append(currentQuestion);
        }
        return context.toString();
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
