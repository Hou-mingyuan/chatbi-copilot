package com.chatbi.copilot.text2sql;

import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import com.chatbi.copilot.text2sql.service.ClarificationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationPolicyTest {
    private final ClarificationPolicy policy = new ClarificationPolicy();

    @Test
    void requiresContextForFirstTurnReferencesAndFilterOnlyQuestions() {
        assertThat(policy.evaluate("这些里面只看华东", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("MISSING_CONTEXT"));
        assertThat(policy.evaluate("只看华东", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("MISSING_METRIC"));
    }

    @Test
    void acceptsAFilterOnlyFollowUpWhenServerHistoryExists() {
        HistoryTurn turn = new HistoryTurn();
        turn.setQuestion("各产品类目的销售额");
        turn.setSql("SELECT category, SUM(amount) FROM order_items GROUP BY category");
        assertThat(policy.evaluate("只看华东", List.of(turn))).isEmpty();
    }

    @Test
    void clarifiesVagueTimeAndRejectsNonReadOnlyIntent() {
        assertThat(policy.evaluate("最近销售趋势", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("AMBIGUOUS_TIME_RANGE"));
        assertThat(policy.evaluate("删除所有订单", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("UNSUPPORTED_OPERATION"));
        assertThat(policy.evaluate("2024 年每月销售额趋势", List.of())).isEmpty();
    }

    @Test
    void clarifiesMissingMetricDimensionAndTimeRange() {
        assertThat(policy.evaluate("2024年华东的数据。", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("AMBIGUOUS_METRIC"));
        assertThat(policy.evaluate("2024年销售额分别是多少？", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("MISSING_DIMENSION"));
        assertThat(policy.evaluate("按月统计销售额。", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("AMBIGUOUS_TIME_RANGE"));
        assertThat(policy.evaluate("按月统计2024年已支付订单的销售额趋势。", List.of())).isEmpty();
    }

    @Test
    void treatsFenbieAsMetricEnumerationWhenTwoMetricsAreExplicit() {
        assertThat(policy.evaluate("已支付订单数量和金额分别是多少？", List.of())).isEmpty();
        assertThat(policy.evaluate("2024年销售额分别是多少？", List.of()))
                .hasValueSatisfying(decision -> assertThat(decision.code()).isEqualTo("MISSING_DIMENSION"));
    }
}
