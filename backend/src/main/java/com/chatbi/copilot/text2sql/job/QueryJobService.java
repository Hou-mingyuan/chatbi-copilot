package com.chatbi.copilot.text2sql.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.QueryJobProperties;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.text2sql.dto.RunSqlRequest;
import com.chatbi.copilot.text2sql.job.mapper.QueryJobMapper;
import com.chatbi.copilot.text2sql.service.QueryCancellationRegistry;
import com.chatbi.copilot.text2sql.service.QueryProgress;
import com.chatbi.copilot.text2sql.service.Text2SqlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class QueryJobService implements ApplicationRunner {
    private static final Set<String> TERMINAL = Set.of(
            "SUCCEEDED", "PREVIEWED", "CLARIFICATION", "NEEDS_CONFIRMATION",
            "FAILED", "CANCELLED", "TIMED_OUT");
    private static final Set<String> RETRYABLE = Set.of("FAILED", "CANCELLED", "TIMED_OUT", "NEEDS_CONFIRMATION");

    private final QueryJobMapper mapper;
    private final Text2SqlService queryService;
    private final HistoryService historyService;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;
    private final QueryCancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final ThreadPoolTaskExecutor executor;
    private final ScheduledExecutorService timeoutScheduler;
    private final QueryJobProperties properties;
    private final Map<String, JobControl> controls = new ConcurrentHashMap<>();

    public QueryJobService(QueryJobMapper mapper, Text2SqlService queryService,
                           HistoryService historyService, CurrentUserService currentUser,
                           DataAccessPolicy accessPolicy, QueryCancellationRegistry cancellationRegistry,
                           ObjectMapper objectMapper, AuditService audit,
                           @Qualifier("queryTaskExecutor") ThreadPoolTaskExecutor executor,
                           ScheduledExecutorService timeoutScheduler, QueryJobProperties properties) {
        this.mapper = mapper;
        this.queryService = queryService;
        this.historyService = historyService;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
        this.cancellationRegistry = cancellationRegistry;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.executor = executor;
        this.timeoutScheduler = timeoutScheduler;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        mapper.update(null, new LambdaUpdateWrapper<QueryJob>()
                .set(QueryJob::getStatus, "FAILED")
                .set(QueryJob::getErrorMessage, "Service restarted before the query completed")
                .set(QueryJob::getMessage, "查询因服务重启而终止")
                .set(QueryJob::getFinishedAt, now())
                .notIn(QueryJob::getStatus, TERMINAL));
    }

    public QueryJobVo createAsk(AskRequest request) {
        return create("ASK", request.getDatasourceId(), request.getSessionId(), request);
    }

    public QueryJobVo createRun(RunSqlRequest request) {
        return create("RUN", request.getDatasourceId(), request.getSessionId(), request);
    }

    public QueryJobVo get(String id) {
        return toVo(requiredOwned(id), true);
    }

    public List<QueryJobVo> list(Long datasourceId) {
        AppUserPrincipal user = currentUser.required();
        if (datasourceId != null) {
            accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        }
        return mapper.selectList(new LambdaQueryWrapper<QueryJob>()
                        .eq(QueryJob::getUserId, user.userId())
                        .eq(datasourceId != null, QueryJob::getDatasourceId, datasourceId)
                        .orderByDesc(QueryJob::getCreatedAt)
                        .last("LIMIT 50"))
                .stream().map(job -> toVo(job, false)).toList();
    }

    public QueryJobVo cancel(String id) {
        QueryJob job = requiredOwned(id);
        if (TERMINAL.contains(job.getStatus())) {
            return toVo(job, true);
        }
        JobControl control = controls.get(id);
        if (control != null) {
            control.cancelled.set(true);
        }
        cancellationRegistry.cancel(id);
        if (control != null && control.future != null) {
            control.future.cancel(true);
        }
        finishCancellation(job, "CANCELLED", "查询已取消");
        audit.record("QUERY_JOB_CANCEL", "QUERY_JOB", id, "SUCCESS", Map.of());
        return toVo(mapper.selectById(id), true);
    }

    public QueryJobVo retry(String id, boolean confirmRisk) {
        QueryJob original = requiredOwned(id);
        if (!RETRYABLE.contains(original.getStatus())) {
            throw new BusinessException("Only failed, cancelled, timed-out, or confirmation jobs can be retried");
        }
        try {
            if ("ASK".equals(original.getJobType())) {
                AskRequest request = objectMapper.readValue(original.getRequestJson(), AskRequest.class);
                request.setConfirmRisk(confirmRisk);
                return createAsk(request);
            }
            RunSqlRequest request = objectMapper.readValue(original.getRequestJson(), RunSqlRequest.class);
            request.setConfirmRisk(confirmRisk);
            return createRun(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored query job request is invalid", e);
        }
    }

    private QueryJobVo create(String type, Long datasourceId, String sessionId, Object request) {
        AppUserPrincipal user = currentUser.required();
        accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        Long active = mapper.selectCount(new LambdaQueryWrapper<QueryJob>()
                .eq(QueryJob::getUserId, user.userId())
                .notIn(QueryJob::getStatus, TERMINAL));
        if (active != null && active >= properties.getMaxActivePerUser()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many active query jobs; wait for one to finish or cancel it");
        }

        QueryJob job = new QueryJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(user.userId());
        job.setDatasourceId(datasourceId);
        job.setSessionId(sessionId);
        job.setJobType(type);
        job.setRequestJson(toJson(request));
        job.setStatus("QUEUED");
        job.setProgress(0);
        job.setMessage("查询已排队");
        job.setCancelRequested(0);
        mapper.insert(job);
        dispatch(job, user);
        return toVo(job, false);
    }

    private void dispatch(QueryJob job, AppUserPrincipal principal) {
        JobControl control = new JobControl(principal);
        controls.put(job.getId(), control);
        try {
            control.future = executor.submit(secured(principal, () -> execute(job.getId(), control)));
            control.timeout = timeoutScheduler.schedule(
                    () -> withSecurity(principal, () -> timeout(job.getId(), control)),
                    properties.getTotalTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            controls.remove(job.getId());
            fail(job.getId(), "Query queue is full");
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Query queue is full; retry after an active query finishes");
        }
    }

    private void execute(String id, JobControl control) {
        try {
            QueryJob job = mapper.selectById(id);
            if (job == null || control.cancelled.get()) {
                throw new CancellationException();
            }
            job.setStartedAt(now());
            mapper.updateById(job);
            QueryProgress progress = progress(id, control);
            QueryResult result;
            if ("ASK".equals(job.getJobType())) {
                AskRequest request = objectMapper.readValue(job.getRequestJson(), AskRequest.class);
                result = queryService.ask(request, progress);
            } else {
                RunSqlRequest request = objectMapper.readValue(job.getRequestJson(), RunSqlRequest.class);
                result = queryService.run(request.getDatasourceId(), request.getSql(), request.isConfirmRisk(),
                        id, request.getSessionId(), progress);
            }
            progress.checkCancelled();
            complete(id, result);
            audit.record("QUERY_JOB_COMPLETE", "QUERY_JOB", id, "SUCCESS",
                    Map.of("status", terminalStatus(result),
                            "queryId", result.getQueryId() == null ? "" : result.getQueryId()));
        } catch (CancellationException e) {
            QueryJob job = mapper.selectById(id);
            if (job != null && !control.timedOut.get() && !"TIMED_OUT".equals(job.getStatus())) {
                finishCancellation(job, "CANCELLED", "查询已取消");
            }
        } catch (Exception e) {
            if (!control.cancelled.get()) {
                String message = e instanceof BusinessException ? e.getMessage() : "Query processing failed";
                fail(id, message);
                audit.record("QUERY_JOB_COMPLETE", "QUERY_JOB", id, "FAILURE", Map.of("error", message));
            }
        } finally {
            if (control.timeout != null) {
                control.timeout.cancel(false);
            }
            controls.remove(id, control);
        }
    }

    private QueryProgress progress(String id, JobControl control) {
        return new QueryProgress() {
            @Override
            public void stage(String status, int progress, String message) {
                checkCancelled();
                QueryJob update = new QueryJob();
                update.setId(id);
                update.setStatus(status);
                update.setProgress(Math.max(0, Math.min(99, progress)));
                update.setMessage(message);
                mapper.updateById(update);
            }

            @Override
            public void checkCancelled() {
                if (control.cancelled.get() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Query job was cancelled");
                }
            }
        };
    }

    private void complete(String id, QueryResult result) {
        QueryJob update = new QueryJob();
        update.setId(id);
        update.setSessionId(result.getSessionId());
        update.setStatus(terminalStatus(result));
        update.setProgress(100);
        update.setMessage(terminalMessage(result));
        update.setResultQueryId(result.getQueryId());
        update.setFinishedAt(now());
        mapper.updateById(update);
    }

    private String terminalStatus(QueryResult result) {
        if (result.isNeedClarification()) return "CLARIFICATION";
        if (result.isRequiresConfirmation()) return "NEEDS_CONFIRMATION";
        return result.getRows().isEmpty() && result.getSql() != null && result.getSummary() == null
                ? "PREVIEWED" : "SUCCEEDED";
    }

    private String terminalMessage(QueryResult result) {
        if (result.isNeedClarification()) return "需要补充问题信息";
        if (result.isRequiresConfirmation()) return "执行计划需要确认";
        return "查询已完成";
    }

    private void timeout(String id, JobControl control) {
        QueryJob job = mapper.selectById(id);
        if (job == null || TERMINAL.contains(job.getStatus())) {
            return;
        }
        control.timedOut.set(true);
        control.cancelled.set(true);
        cancellationRegistry.cancel(id);
        if (control.future != null) {
            control.future.cancel(true);
        }
        finishCancellation(job, "TIMED_OUT", "查询超过总时限，已终止");
        audit.record("QUERY_JOB_TIMEOUT", "QUERY_JOB", id, "FAILURE",
                Map.of("timeoutSeconds", properties.getTotalTimeoutSeconds()));
    }

    private void finishCancellation(QueryJob job, String status, String message) {
        job.setStatus(status);
        job.setCancelRequested(1);
        job.setMessage(message);
        job.setErrorMessage(message);
        job.setFinishedAt(now());
        mapper.updateById(job);
    }

    private void fail(String id, String error) {
        QueryJob update = new QueryJob();
        update.setId(id);
        update.setStatus("FAILED");
        update.setMessage("查询失败");
        update.setErrorMessage(limit(error));
        update.setFinishedAt(now());
        mapper.updateById(update);
    }

    private QueryJob requiredOwned(String id) {
        QueryJob job = mapper.selectById(id);
        AppUserPrincipal user = currentUser.required();
        if (job == null || !user.userId().equals(job.getUserId())) {
            throw BusinessException.notFound("Query job not found");
        }
        return job;
    }

    private QueryJobVo toVo(QueryJob job, boolean includeResult) {
        QueryResult result = null;
        if (includeResult && job.getResultQueryId() != null) {
            result = historyService.result(job.getResultQueryId(), Capability.QUERY);
        }
        return new QueryJobVo(job.getId(), job.getDatasourceId(), job.getSessionId(), job.getJobType(),
                job.getStatus(), job.getProgress() == null ? 0 : job.getProgress(), job.getMessage(),
                job.getResultQueryId(), job.getErrorMessage(), job.getCreatedAt(), job.getStartedAt(),
                job.getFinishedAt(), result);
    }

    private Runnable secured(AppUserPrincipal principal, Runnable runnable) {
        return () -> withSecurity(principal, runnable);
    }

    private void withSecurity(AppUserPrincipal principal, Runnable runnable) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            runnable.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Query job request could not be serialized", e);
        }
    }

    private String limit(String value) {
        if (value == null) return "Query processing failed";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static class JobControl {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean timedOut = new AtomicBoolean();
        private volatile Future<?> future;
        private volatile ScheduledFuture<?> timeout;

        private JobControl(AppUserPrincipal principal) {
        }
    }
}
