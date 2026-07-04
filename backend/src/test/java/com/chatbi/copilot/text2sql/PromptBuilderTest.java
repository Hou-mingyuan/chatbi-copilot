package com.chatbi.copilot.text2sql;

import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import com.chatbi.copilot.text2sql.service.PromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setup() {
        promptBuilder = new PromptBuilder(new SqlGuardProperties());
    }

    private SchemaInfo sampleSchema() {
        SchemaInfo info = new SchemaInfo();
        info.setDatasourceId(1L);
        info.setDatabaseName("shop");
        info.setDbType("mysql");

        TableSchema orders = new TableSchema();
        orders.setName("orders");
        orders.setComment("订单表");
        orders.setBusinessAlias("订单");

        ColumnSchema id = new ColumnSchema();
        id.setName("id");
        id.setDataType("BIGINT");
        id.setPrimaryKey(true);
        id.setComment("主键");
        orders.getColumns().add(id);

        ColumnSchema amount = new ColumnSchema();
        amount.setName("amount");
        amount.setDataType("DECIMAL");
        amount.setComment("金额");
        amount.setBusinessAlias("成交额");
        orders.getColumns().add(amount);

        info.getTables().add(orders);
        return info;
    }

    @Test
    @DisplayName("renderSchema includes tables, columns, comments and aliases")
    void renderSchemaContainsMetadata() {
        String rendered = promptBuilder.renderSchema(sampleSchema());
        assertTrue(rendered.contains("orders"), rendered);
        assertTrue(rendered.contains("amount"), rendered);
        assertTrue(rendered.contains("DECIMAL"), rendered);
        assertTrue(rendered.contains("订单表"), rendered);
        assertTrue(rendered.contains("成交额"), rendered);
        assertTrue(rendered.contains("PRIMARY KEY"), rendered);
    }

    @Test
    @DisplayName("System prompt states the dialect and JSON output contract")
    void systemPromptHasDialectAndContract() {
        String prompt = promptBuilder.buildSystemPrompt(sampleSchema());
        assertTrue(prompt.contains("MySQL"), prompt);
        assertTrue(prompt.contains("needClarification"), prompt);
        assertTrue(prompt.toLowerCase().contains("select"), prompt);
    }

    @Test
    @DisplayName("buildMessages puts system first and the question last")
    void buildMessagesStructure() {
        List<ChatMessage> messages = promptBuilder.buildMessages(sampleSchema(), "各状态订单数量?", null);
        assertEquals("system", messages.get(0).getRole());
        ChatMessage last = messages.get(messages.size() - 1);
        assertEquals("user", last.getRole());
        assertEquals("各状态订单数量?", last.getContent());
    }

    @Test
    @DisplayName("History turns are inserted before the current question")
    void buildMessagesWithHistory() {
        HistoryTurn turn = new HistoryTurn();
        turn.setQuestion("上个月销售额?");
        turn.setSql("SELECT SUM(amount) FROM orders");
        List<ChatMessage> messages =
                promptBuilder.buildMessages(sampleSchema(), "那这个月呢?", List.of(turn));
        // system + user(history) + assistant(history) + user(current) = 4
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("上个月销售额?", messages.get(1).getContent());
        assertEquals("assistant", messages.get(2).getRole());
    }
}
