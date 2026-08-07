package com.chatbi.copilot.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import com.chatbi.copilot.export.ExcelExportService;
import com.chatbi.copilot.history.dto.FavoriteReq;
import com.chatbi.copilot.history.dto.QuerySnapshot;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.entity.QuerySession;
import com.chatbi.copilot.history.mapper.QueryHistoryMapper;
import com.chatbi.copilot.history.service.ConversationService;
import com.chatbi.copilot.history.service.FavoriteService;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.entity.DatasourceAcl;
import com.chatbi.copilot.permission.mapper.DatasourceAclMapper;
import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.text2sql.dto.ResultSummary;
import com.chatbi.copilot.text2sql.plan.QueryRisk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:history-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "server.port=0"
})
@Transactional
class HistorySecurityIntegrationTest {
    @Autowired private AppUserMapper userMapper;
    @Autowired private DataSourceConfigMapper datasourceMapper;
    @Autowired private DatasourceAclMapper aclMapper;
    @Autowired private QueryHistoryMapper historyMapper;
    @Autowired private HistoryService historyService;
    @Autowired private ConversationService conversationService;
    @Autowired private FavoriteService favoriteService;
    @Autowired private ExcelExportService excelExportService;

    private AppUser analyst;
    private AppUser viewer;
    private Long datasourceId;

    @BeforeEach
    void setUp() {
        analyst = user("analyst");
        viewer = user("viewer");
        DataSourceConfig datasource = new DataSourceConfig();
        datasource.setName("History security");
        datasource.setDbType("mysql");
        datasource.setHost("localhost");
        datasource.setPort(3306);
        datasource.setDatabaseName("demo");
        datasource.setUsername("readonly");
        datasource.setPassword("encrypted");
        datasource.setVerifiedReadOnly(1);
        datasourceMapper.insert(datasource);
        datasourceId = datasource.getId();
        acl(analyst.getId(), true, true);
        acl(viewer.getId(), true, false);
        authenticate(analyst, Role.ANALYST);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void snapshotFavoriteAndExcelUseTheOwnedSuccessfulQuery() {
        QueryResult result = successfulResult();
        historyService.record(result, "SUCCEEDED", null, null, null);

        QuerySnapshot snapshot = historyService.snapshot(result.getQueryId(), Capability.EXPORT);
        assertThat(snapshot.rows().get(0).get("paid_amount"))
                .isEqualTo(new BigDecimal("185551.00"));

        FavoriteReq request = new FavoriteReq();
        request.setQueryId(result.getQueryId());
        request.setTitle("Paid order baseline");
        var favorite = favoriteService.create(request);
        assertThat(favorite.getUserId()).isEqualTo(analyst.getId());
        assertThat(favorite.getGeneratedSql()).isEqualTo(result.getSql());
        assertThat(excelExportService.export(result.getQueryId())).isNotEmpty();
    }

    @Test
    void anotherUserAndRevokedExportPermissionCannotReadTheSnapshot() {
        QueryResult result = successfulResult();
        historyService.record(result, "SUCCEEDED", null, null, null);

        authenticate(viewer, Role.VIEWER);
        assertThatThrownBy(() -> historyService.snapshot(result.getQueryId(), Capability.QUERY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        authenticate(analyst, Role.ANALYST);
        aclMapper.update(null, new LambdaUpdateWrapper<DatasourceAcl>()
                .set(DatasourceAcl::getCanExport, 0)
                .eq(DatasourceAcl::getUserId, analyst.getId())
                .eq(DatasourceAcl::getDatasourceId, datasourceId));
        assertThatThrownBy(() -> excelExportService.export(result.getQueryId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void aTamperedSnapshotFailsIntegrityValidation() {
        QueryResult result = successfulResult();
        historyService.record(result, "SUCCEEDED", null, null, null);
        QueryHistory stored = historyMapper.selectById(result.getQueryId());
        stored.setResultSnapshot(stored.getResultSnapshot().replace("185551.00", "999999.00"));
        historyMapper.updateById(stored);

        assertThatThrownBy(() -> historyService.snapshot(result.getQueryId(), Capability.EXPORT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("integrity");
    }

    @Test
    void confirmationResultRestoresTheExactExecutionPlanRisk() {
        QueryResult result = new QueryResult();
        result.setDatasourceId(datasourceId);
        result.setQuestion("全量订单趋势");
        result.setSql("SELECT created_at, amount FROM orders LIMIT 500");
        result.setRequiresConfirmation(true);
        result.setRisk(new QueryRisk("MEDIUM", 250000L, true, true, false,
                List.of("estimated rows exceed confirmation threshold"),
                List.of("full table scan on orders")));
        historyService.record(result, "NEEDS_CONFIRMATION", null, null, null);

        QueryResult restored = historyService.result(result.getQueryId(), Capability.QUERY);
        assertThat(restored.isRequiresConfirmation()).isTrue();
        assertThat(restored.getRisk()).isEqualTo(result.getRisk());
        assertThat(restored.getRisk().planSummary()).containsExactly("full table scan on orders");
    }

    @Test
    void clarificationTurnIsAvailableOnlyInsideTheOwnedServerSessionContext() {
        QuerySession session = conversationService.resolve(datasourceId, null, "2024年各大区怎么样？");
        QueryResult clarification = new QueryResult();
        clarification.setDatasourceId(datasourceId);
        clarification.setSessionId(session.getId());
        clarification.setQuestion("2024年各大区怎么样？");
        clarification.setNeedClarification(true);
        clarification.setClarification("请说明要分析的指标、分组维度和时间范围。");
        historyService.record(clarification, "CLARIFICATION", null, null, null);

        var context = conversationService.context(session.getId());
        assertThat(context).singleElement().satisfies(turn -> {
            assertThat(turn.getQuestion()).isEqualTo("2024年各大区怎么样？");
            assertThat(turn.getClarification()).isEqualTo("请说明要分析的指标、分组维度和时间范围。");
            assertThat(turn.getSql()).isNull();
        });

        authenticate(viewer, Role.VIEWER);
        assertThat(conversationService.context(session.getId())).isEmpty();
    }

    private QueryResult successfulResult() {
        QueryResult result = new QueryResult();
        result.setDatasourceId(datasourceId);
        result.setQuestion("已支付订单数量和金额");
        result.setSql("SELECT COUNT(*) paid_orders, SUM(amount) paid_amount FROM orders WHERE status='paid' LIMIT 500");
        result.setColumns(List.of(
                new ColumnMeta("paid_orders", "BIGINT", "measure"),
                new ColumnMeta("paid_amount", "DECIMAL", "measure")));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paid_orders", 20L);
        row.put("paid_amount", new BigDecimal("185551.00"));
        result.setRows(List.of(row));
        result.setRowCount(1);
        result.setElapsedMs(12);
        result.setSummary(new ResultSummary("paid_orders为 20，paid_amount为 185551.00。", List.of()));
        result.setExplanation(result.getSummary().text());
        return result;
    }

    private AppUser user(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username));
    }

    private void acl(Long userId, boolean query, boolean export) {
        DatasourceAcl acl = new DatasourceAcl();
        acl.setUserId(userId);
        acl.setDatasourceId(datasourceId);
        acl.setCanQuery(query ? 1 : 0);
        acl.setCanExport(export ? 1 : 0);
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
