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
        boolean postgres = messages != null && messages.stream()
                .filter(message -> message != null && "system".equalsIgnoreCase(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(java.util.Objects::nonNull)
                .anyMatch(content -> content.contains("PostgreSQL"));
        return toJson(buildAnswer(question, postgres));
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
        return buildAnswer(question, false);
    }

    static Map<String, Object> buildAnswer(String question, boolean postgres) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (containsAny(q, "删除", "修改", "写入", "密码", "口令", "绕过", "drop", "delete", "update", "password")) {
            return clarification("Mock 仅支持已授权的只读问数，不能执行该请求。");
        }

        if (containsAny(q, "大区", "区域", "region")
                && containsAny(q, "订单", "order")
                && containsAny(q, "数量", "订单数", "count")) {
            return answer("""
                    SELECT c.region AS region, COUNT(*) AS paid_order_count
                    FROM orders o
                    JOIN customers c ON o.customer_id = c.id
                    WHERE o.status = 'paid'
                    GROUP BY c.region
                    ORDER BY paid_order_count DESC
                    LIMIT 500
                    """, "Mock：各大区已支付订单数量。");
        }

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
            String month = postgres
                    ? "TO_CHAR(o.order_date, 'YYYY-MM')"
                    : "DATE_FORMAT(o.order_date, '%Y-%m')";
            String year = postgres
                    ? "EXTRACT(YEAR FROM o.order_date) = 2024"
                    : "YEAR(o.order_date) = 2024";
            return answer(
                    """
                    SELECT %s AS month, SUM(o.total_amount) AS total_sales
                    FROM orders o
                    WHERE o.status = 'paid' AND %s
                    GROUP BY %s
                    ORDER BY month
                    LIMIT 500
                    """.formatted(month, year, month),
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
        if (containsAny(q, "大区", "区域", "region") && containsAny(q, "客户", "人数", "customer")) {
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

        if (containsAny(q, "大区", "区域", "region") && containsAny(q, "销售", "金额", "sales", "revenue")) {
            return answer("""
                    SELECT c.region AS region, SUM(o.total_amount) AS total_sales
                    FROM orders o
                    JOIN customers c ON o.customer_id = c.id
                    WHERE o.status = 'paid'
                    GROUP BY c.region
                    ORDER BY total_sales DESC
                    LIMIT 500
                    """, "Mock：各大区已支付订单销售额。");
        }

        if (containsAny(q, "平均", "客单价", "average", "avg")) {
            return answer("""
                    SELECT AVG(o.total_amount) AS average_order_amount
                    FROM orders o
                    WHERE o.status = 'paid'
                    LIMIT 500
                    """, "Mock：已支付订单平均金额。");
        }

        if (containsAny(q, "状态", "status") && containsAny(q, "分布", "数量", "count")) {
            return answer("""
                    SELECT o.status AS order_status, COUNT(*) AS order_count
                    FROM orders o
                    GROUP BY o.status
                    ORDER BY order_count DESC
                    LIMIT 500
                    """, "Mock：各状态订单数量。");
        }

        if (containsAny(q, "已支付", "paid")
                && containsAny(q, "数量", "订单数", "count")
                && containsAny(q, "金额", "销售额", "sales", "amount", "revenue")) {
            return answer("""
                    SELECT COUNT(*) AS paid_order_count, SUM(o.total_amount) AS paid_order_amount
                    FROM orders o
                    WHERE o.status = 'paid'
                    LIMIT 500
                    """, "Mock：已支付订单数量和金额。");
        }

        if (containsAny(q, "退款", "refunded") && containsAny(q, "多少", "数量", "count")) {
            return answer("""
                    SELECT COUNT(*) AS refunded_order_count
                    FROM orders o
                    WHERE o.status = 'refunded'
                    LIMIT 500
                    """, "Mock：已退款订单总数。");
        }

        if (containsAny(q, "销售总额", "销售额", "总金额", "total sales", "revenue")) {
            return answer("""
                    SELECT SUM(o.total_amount) AS total_sales
                    FROM orders o
                    WHERE o.status = 'paid'
                    LIMIT 500
                    """, "Mock：已支付订单销售总额。");
        }

        if (containsAny(q, "订单", "order") && containsAny(q, "多少", "数量", "count")) {
            return answer("""
                    SELECT COUNT(*) AS paid_order_count
                    FROM orders o
                    WHERE o.status = 'paid'
                    LIMIT 500
                    """, "Mock：已支付订单总数。");
        }

        return clarification("Mock 无法可靠映射该问题，请明确指标、维度和时间范围。");
    }

    private static Map<String, Object> answer(String sql, String explanation) {
        return Map.of(
                "sql", sql.strip(),
                "explanation", explanation,
                "needClarification", false);
    }

    private static Map<String, Object> clarification(String message) {
        return Map.of("sql", "", "explanation", "", "needClarification", true,
                "clarification", message);
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
