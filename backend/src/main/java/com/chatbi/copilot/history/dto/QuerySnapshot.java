package com.chatbi.copilot.history.dto;

import com.chatbi.copilot.chart.ChartRecommendation;
import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import com.chatbi.copilot.text2sql.dto.ResultSummary;
import com.chatbi.copilot.text2sql.plan.QueryRisk;

import java.util.List;
import java.util.Map;

public record QuerySnapshot(List<ColumnMeta> columns, List<Map<String, Object>> rows,
                            int rowCount, boolean truncated, long elapsedMs,
                            ChartRecommendation chart, ResultSummary summary, QueryRisk risk) {
}
