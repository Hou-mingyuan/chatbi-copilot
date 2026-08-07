package com.chatbi.copilot.llm;

import com.chatbi.copilot.llm.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmChatClientTest {

    @Test
    void isConfiguredWithoutApiKey() {
        assertThat(new MockLlmChatClient().isConfigured()).isTrue();
    }

    @Test
    void categoryShareQuestionReturnsGroupedSql() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("各产品类目的销售额占比")));
        assertThat(raw).contains("p.category");
        assertThat(raw).contains("total_sales");
        assertThat(raw).contains("\"needClarification\":false");
    }

    @Test
    void eastChinaFollowUpAddsRegionFilter() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("只看华东大区")));
        assertThat(raw).contains("c.region = '华东'");
    }

    @Test
    void regionPaidOrderCountUsesOrdersInsteadOfCustomerCount() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("各大区的已支付订单数量分别是多少？")));
        assertThat(raw)
                .contains("COUNT(*) AS paid_order_count")
                .contains("FROM orders o")
                .contains("o.status = 'paid'")
                .doesNotContain("customer_count");
    }

    @Test
    void englishRegionOrderCountUsesTheSameSafePreset() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("paid order count by region")));
        assertThat(raw)
                .contains("COUNT(*) AS paid_order_count")
                .contains("JOIN customers c")
                .doesNotContain("customer_count");
    }

    @Test
    void explicitRegionCustomerCountRemainsAvailable() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("各大区客户数量")));
        assertThat(raw)
                .contains("COUNT(*) AS customer_count")
                .contains("FROM customers c");
    }

    @Test
    void emitsPostgresqlMonthSyntaxWhenPromptRequestsThatDialect() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.system("Use PostgreSQL dialect"),
                ChatMessage.user("2024 年每月销售额趋势")));
        assertThat(raw).contains("TO_CHAR", "EXTRACT(YEAR");
        assertThat(raw).doesNotContain("DATE_FORMAT", "YEAR(");
    }

    @Test
    void paidCountAndSalesPresetReturnsBothMetrics() {
        String raw = new MockLlmChatClient().chat(java.util.List.of(
                ChatMessage.user("已支付订单的数量和销售额是多少？")));
        assertThat(raw).contains("paid_order_count", "paid_order_amount");
        assertThat(raw).contains("COUNT(*)", "SUM(o.total_amount)");
        assertThat(raw).contains("\"needClarification\":false");
    }

    @Test
    void unknownAndUnsafeQuestionsDoNotSilentlyBecomePaidOrderCount() {
        String unknown = new MockLlmChatClient().chat(java.util.List.of(ChatMessage.user("天气怎么样")));
        String unsafe = new MockLlmChatClient().chat(java.util.List.of(ChatMessage.user("删除所有订单")));
        assertThat(unknown).contains("\"needClarification\":true", "\"sql\":\"\"");
        assertThat(unsafe).contains("\"needClarification\":true", "只读");
    }
}
