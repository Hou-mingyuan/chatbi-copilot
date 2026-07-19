package com.chatbi.copilot.llm;

import com.chatbi.copilot.llm.dto.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline mock LLM for portfolio demo / CI. Maps common demo questions to safe SELECT SQL
 * against the bundled chatbi_demo schema without calling an external API.
 */
public class MockLlmChatClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        String question = extractLastUserQuestion(messages);
        return toJson(buildAnswer(question));
    }

    static String extractLastUserQuestion(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent().trim();
            }
        }
        return "";
    }

    static Map<String, Object> buildAnswer(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (containsAny(q, "华东", "华北", "华南", "华中", "西南") && !containsAny(q, "客户", "人数")) {
            String region = firstRegion(q);
            return answer(
                    """
                    SELECT p.category AS category, SUM(oi.amount) AS total_sales
                    FROM order_items oi
                    JOIN products p ON oi.product_id = p.id
                    JOIN orders o ON oi.order_id = o.id
                    JOIN customers c ON o.customer_id = c.id
                    WHERE o.status = 'paid' AND c.region = '%s'
                    GROUP BY p.category
                    ORDER BY total_sales DESC
                    LIMIT 500
                    """.formatted(region),
                    "Mock：" + region + "大区各产品类目销售额汇总。");
        }
        if (containsAny(q, "类目", "占比", "类别")) {
            return answer(
                    """
                    SELECT p.category AS category, SUM(oi.amount) AS total_sales
                    FROM order_items oi
                    JOIN products p ON oi.product_id = p.id
                    JOIN orders o ON oi.order_id = o.id
                    WHERE o.status = 'paid'
                    GROUP BY p.category
                    ORDER BY total_sales DESC
                    LIMIT 500
                    """,
                    "Mock：各产品类目销售额汇总，可用于占比图表。");
        }
        if (containsAny(q, "每月", "月度", "趋势") || (q.contains("2024") && containsAny(q, "销售", "金额"))) {
            return answer(
                    """
                    SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS month, SUM(o.total_amount) AS total_sales
                    FROM orders o
                    WHERE o.status = 'paid' AND YEAR(o.order_date) = 2024
                    GROUP BY DATE_FORMAT(o.order_date, '%Y-%m')
                    ORDER BY month
                    LIMIT 500
                    """,
                    "Mock：2024 年按月汇总已支付订单销售额。");
        }
        if (containsAny(q, "最高", "top", "前") && containsAny(q, "产品", "商品", "5")) {
            return answer(
                    """
                    SELECT p.name AS product_name, SUM(oi.amount) AS total_sales
                    FROM order_items oi
                    JOIN products p ON oi.product_id = p.id
                    JOIN orders o ON oi.order_id = o.id
                    WHERE o.status = 'paid'
                    GROUP BY p.id, p.name
                    ORDER BY total_sales DESC
                    LIMIT 5
                    """,
                    "Mock：销售额 Top 5 产品。");
        }
        if (containsAny(q, "大区", "区域", "region") && containsAny(q, "客户", "数量", "人数")) {
            return answer(
                    """
                    SELECT c.region AS region, COUNT(*) AS customer_count
                    FROM customers c
                    GROUP BY c.region
                    ORDER BY customer_count DESC
                    LIMIT 500
                    """,
                    "Mock：各大区客户数量。");
        }

        return answer(
                """
                SELECT COUNT(*) AS paid_order_count
                FROM orders o
                WHERE o.status = 'paid'
                LIMIT 500
                """,
                "Mock 默认：已支付订单总数。可尝试「各产品类目的销售额占比」等演示问句。");
    }

    private static Map<String, Object> answer(String sql, String explanation) {
        return Map.of(
                "sql", sql.strip(),
                "explanation", explanation,
                "needClarification", false);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String firstRegion(String text) {
        for (String region : new String[] {"华东", "华北", "华南", "华中", "西南"}) {
            if (text.contains(region)) {
                return region;
            }
        }
        return "华东";
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize mock LLM answer", e);
        }
    }
}
