package com.chatbi.copilot.text2sql;

import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.ResultSummary;
import com.chatbi.copilot.text2sql.service.ResultInterpreter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultInterpreterTest {
    private final ResultInterpreter interpreter = new ResultInterpreter();

    @Test
    void derivesSingleRowFactsFromExecutedValuesWithoutRounding() {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("paid_orders", "BIGINT", "measure"),
                new ColumnMeta("paid_amount", "DECIMAL", "measure"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paid_orders", 20L);
        row.put("paid_amount", new BigDecimal("185551.00"));

        ResultSummary summary = interpreter.interpret(columns, List.of(row), false);

        assertThat(summary.text()).contains("paid_orders为 20", "paid_amount为 185551.00");
        assertThat(summary.facts()).extracting(fact -> fact.value())
                .contains("1", "20", "185551.00");
    }

    @Test
    void reportsMaximumOnlyFromRowsAndDisclosesTruncation() {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("region", "VARCHAR", "dimension"),
                new ColumnMeta("sales", "DECIMAL", "measure"));
        List<Map<String, Object>> rows = List.of(
                Map.of("region", "East", "sales", new BigDecimal("10.25")),
                Map.of("region", "West", "sales", new BigDecimal("42.50")));

        ResultSummary summary = interpreter.interpret(columns, rows, true);

        assertThat(summary.text()).contains("2 行", "已按安全行数上限截断", "West：42.50");
    }
}
