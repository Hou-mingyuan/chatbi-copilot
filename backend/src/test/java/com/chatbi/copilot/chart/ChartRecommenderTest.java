package com.chatbi.copilot.chart;

import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChartRecommenderTest {
    private final ChartRecommender recommender = new ChartRecommender();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chartFieldsAlwaysReferenceTheSameResultColumns() {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("month", "DATE", null),
                new ColumnMeta("amount", "DECIMAL", null));
        var recommendation = recommender.recommend(columns, List.of(
                Map.of("month", "2026-01-01", "amount", new BigDecimal("10.25")),
                Map.of("month", "2026-02-01", "amount", new BigDecimal("20.50"))));

        assertThat(recommendation.getType()).isEqualTo("line");
        assertThat(recommendation.getXField()).isEqualTo("month");
        assertThat(recommendation.getYFields()).containsExactly("amount");
    }

    @Test
    void unsafePrecisionStringsFallBackToTableInsteadOfChangingChartValues() {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("region", "VARCHAR", null),
                new ColumnMeta("amount", "DECIMAL", null));
        var recommendation = recommender.recommend(columns,
                List.of(Map.of("region", "East", "amount", "12345678901234567890.12")));

        assertThat(recommendation.getType()).isEqualTo("table");
    }

    @Test
    void chartJsonUsesFrontendFieldNamesAndReadsLegacySnapshots() throws Exception {
        ChartRecommendation recommendation = new ChartRecommendation();
        recommendation.setXField("region");
        recommendation.setYFields(List.of("paid_order_count"));
        recommendation.setSeriesField("channel");

        String json = objectMapper.writeValueAsString(recommendation);
        assertThat(json).contains("\"xField\":\"region\"", "\"yFields\":[\"paid_order_count\"]",
                "\"seriesField\":\"channel\"");
        assertThat(json).doesNotContain("\"xfield\"", "\"yfields\"", "\"seriesfield\"");

        ChartRecommendation restored = objectMapper.readValue(
                "{\"xfield\":\"region\",\"yfields\":[\"paid_order_count\"],\"seriesfield\":\"channel\"}",
                ChartRecommendation.class);
        assertThat(restored.getXField()).isEqualTo("region");
        assertThat(restored.getYFields()).containsExactly("paid_order_count");
        assertThat(restored.getSeriesField()).isEqualTo("channel");
    }
}
