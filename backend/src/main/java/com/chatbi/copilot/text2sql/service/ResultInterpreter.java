package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.InsightFact;
import com.chatbi.copilot.text2sql.dto.ResultSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds a deterministic explanation exclusively from the executed result set. */
@Component
public class ResultInterpreter {
    private static final int MAX_FACTS = 4;

    public ResultSummary interpret(List<ColumnMeta> columns, List<Map<String, Object>> rows,
                                   boolean truncated) {
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        List<ColumnMeta> safeColumns = columns == null ? List.of() : columns;
        List<InsightFact> facts = new ArrayList<>();
        facts.add(new InsightFact("返回行数", String.valueOf(safeRows.size()), null, "行"));

        if (safeRows.isEmpty()) {
            return new ResultSummary("查询未返回数据。", List.copyOf(facts));
        }

        if (safeRows.size() == 1) {
            Map<String, Object> row = safeRows.get(0);
            for (ColumnMeta column : safeColumns) {
                Object value = row.get(column.getName());
                if (value != null && facts.size() < MAX_FACTS) {
                    facts.add(new InsightFact(column.getName(), exactText(value), column.getName(), null));
                }
            }
            String details = facts.stream().skip(1)
                    .map(fact -> fact.label() + "为 " + fact.value())
                    .reduce((left, right) -> left + "，" + right).orElse("返回 1 行数据");
            return new ResultSummary(details + "。", List.copyOf(facts));
        }

        ColumnMeta measure = safeColumns.stream()
                .filter(column -> "measure".equals(column.getCategory()))
                .filter(column -> safeRows.stream().anyMatch(row -> asDecimal(row.get(column.getName())) != null))
                .findFirst().orElse(null);
        ColumnMeta dimension = safeColumns.stream()
                .filter(column -> !"measure".equals(column.getCategory()))
                .findFirst().orElse(null);

        if (measure != null && dimension != null) {
            Map<String, Object> maximum = safeRows.stream()
                    .filter(row -> asDecimal(row.get(measure.getName())) != null)
                    .max((left, right) -> asDecimal(left.get(measure.getName()))
                            .compareTo(asDecimal(right.get(measure.getName()))))
                    .orElse(null);
            if (maximum != null) {
                String label = exactText(maximum.get(dimension.getName()));
                String value = exactText(maximum.get(measure.getName()));
                facts.add(new InsightFact("最高" + measure.getName(), label + "：" + value,
                        measure.getName(), null));
            }
        }

        String suffix = truncated ? "，结果已按安全行数上限截断" : "";
        String text = "查询返回 " + safeRows.size() + " 行" + suffix;
        if (facts.size() > 1) {
            text += "；" + facts.get(1).label() + "为 " + facts.get(1).value();
        }
        return new ResultSummary(text + "。", List.copyOf(facts));
    }

    private BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? BigDecimal.valueOf(number) : null;
        }
        return null;
    }

    private String exactText(Object value) {
        if (value == null) {
            return "空值";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof BigInteger integer) {
            return integer.toString();
        }
        return String.valueOf(value);
    }
}
