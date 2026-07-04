package com.chatbi.copilot.text2sql.dto;

import com.chatbi.copilot.chart.ChartRecommendation;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full answer returned to the client: the generated SQL, an explanation, the result set and a
 * recommended chart. When {@code needClarification} is true, only the clarification is populated.
 */
@Data
public class QueryResult {

    private Long datasourceId;
    private String question;

    private String sql;
    private String explanation;

    private boolean needClarification;
    private String clarification;

    private List<ColumnMeta> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private int rowCount;
    private boolean truncated;
    private long elapsedMs;

    private ChartRecommendation chart;
}
