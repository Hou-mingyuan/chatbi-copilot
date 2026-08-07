package com.chatbi.copilot.text2sql.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.text2sql.dto.ResultSummary;
import com.chatbi.copilot.text2sql.job.mapper.QueryJobMapper;
import com.chatbi.copilot.text2sql.service.QueryProgress;
import com.chatbi.copilot.text2sql.service.Text2SqlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:query-jobs;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "chatbi.query-jobs.total-timeout-seconds=1",
        "server.port=0"
})
class QueryJobServiceIntegrationTest {
    private static final Long DATASOURCE_ID = 92345L;

    @Autowired private QueryJobService service;
    @Autowired private QueryJobMapper jobMapper;
    @Autowired private AppUserMapper userMapper;
    @Autowired private DatasourceAclMapper aclMapper;
    @MockBean private Text2SqlService queryService;

    private AppUser analyst;
    private AppUser viewer;
    private final AtomicInteger retryAttempts = new AtomicInteger();

    @BeforeEach
    void setUp() {
        jobMapper.delete(new LambdaQueryWrapper<>());
        analyst = user("analyst");
        viewer = user("viewer");
        aclMapper.delete(new LambdaQueryWrapper<DatasourceAcl>()
                .eq(DatasourceAcl::getDatasourceId, DATASOURCE_ID));
        acl(analyst.getId());
        acl(viewer.getId());
        authenticate(analyst, Role.ANALYST);
        retryAttempts.set(0);
        when(queryService.ask(any(AskRequest.class), any(QueryProgress.class)))
                .thenAnswer(invocation -> executeMock(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesRealStagesAndHidesJobsFromOtherUsers() throws Exception {
        QueryJobVo created = service.createAsk(request("clarify"));
        QueryJobVo completed = awaitTerminal(created.id(), 3000);

        assertThat(completed.status()).isEqualTo("CLARIFICATION");
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.sessionId()).isEqualTo("session-1");

        authenticate(viewer, Role.VIEWER);
        assertThatThrownBy(() -> service.get(created.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void cancellationStopsAnExecutingJob() throws Exception {
        QueryJobVo created = service.createAsk(request("slow"));
        awaitStatus(created.id(), "EXECUTING", 3000);

        QueryJobVo cancelled = service.cancel(created.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(awaitTerminal(created.id(), 3000).status()).isEqualTo("CANCELLED");
    }

    @Test
    void totalTimeoutAndRetryArePersisted() throws Exception {
        QueryJobVo timed = service.createAsk(request("timeout"));
        assertThat(awaitTerminal(timed.id(), 4000).status()).isEqualTo("TIMED_OUT");

        QueryJobVo failed = service.createAsk(request("retry"));
        assertThat(awaitTerminal(failed.id(), 3000).status()).isEqualTo("FAILED");
        QueryJobVo retried = service.retry(failed.id(), false);
        assertThat(awaitTerminal(retried.id(), 3000).status()).isEqualTo("SUCCEEDED");
    }

    private QueryResult executeMock(AskRequest request, QueryProgress progress) throws Exception {
        progress.stage("GENERATING_SQL", 25, "generating");
        if ("clarify".equals(request.getQuestion())) {
            QueryResult result = new QueryResult();
            result.setDatasourceId(DATASOURCE_ID);
            result.setSessionId("session-1");
            result.setNeedClarification(true);
            return result;
        }
        if ("retry".equals(request.getQuestion()) && retryAttempts.getAndIncrement() == 0) {
            throw new BusinessException("temporary failure");
        }
        if ("slow".equals(request.getQuestion()) || "timeout".equals(request.getQuestion())) {
            progress.stage("EXECUTING", 75, "executing");
            while (true) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException();
                }
                progress.checkCancelled();
            }
        }
        QueryResult result = new QueryResult();
        result.setDatasourceId(DATASOURCE_ID);
        result.setSessionId("session-2");
        result.setSql("SELECT 1 LIMIT 1");
        result.setSummary(new ResultSummary("ok", List.of()));
        return result;
    }

    private QueryJobVo awaitTerminal(String id, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        QueryJobVo latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = service.get(id);
            if (Set.of("SUCCEEDED", "PREVIEWED", "CLARIFICATION", "NEEDS_CONFIRMATION",
                    "FAILED", "CANCELLED", "TIMED_OUT").contains(latest.status())) {
                return latest;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Job did not finish; last status=" + (latest == null ? "none" : latest.status()));
    }

    private void awaitStatus(String id, String status, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (status.equals(service.get(id).status())) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Job did not reach " + status);
    }

    private AskRequest request(String question) {
        AskRequest request = new AskRequest();
        request.setDatasourceId(DATASOURCE_ID);
        request.setQuestion(question);
        return request;
    }

    private AppUser user(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
    }

    private void acl(Long userId) {
        DatasourceAcl acl = new DatasourceAcl();
        acl.setUserId(userId);
        acl.setDatasourceId(DATASOURCE_ID);
        acl.setCanQuery(1);
        acl.setCanExport(0);
        acl.setCanManageSemantic(0);
        aclMapper.insert(acl);
    }

    private void authenticate(AppUser user, Role role) {
        AppUserPrincipal principal = new AppUserPrincipal(user.getId(), user.getUsername(),
                user.getDisplayName(), "test-session", "csrf", Set.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
