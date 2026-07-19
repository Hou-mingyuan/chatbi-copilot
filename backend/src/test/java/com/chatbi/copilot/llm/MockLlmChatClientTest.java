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
}
