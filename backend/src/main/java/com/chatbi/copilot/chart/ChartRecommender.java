package com.chatbi.copilot.chart;

import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Heuristically recommends a chart type from the result set shape:
 * <ul>
 *   <li>time dimension + measures -&gt; line</li>
 *   <li>one category dimension + measures -&gt; bar (pie for a small single-measure breakdown)</li>
 *   <li>otherwise -&gt; table</li>
 * </ul>
 * The frontend can always override the suggestion.
 */
@Component
public class ChartRecommender {

    private static final Pattern ID_LIKE = Pattern.compile("(?i)(^id$|_id$|code$|^no$|_no$)");
    private static final int PIE_MAX_SLICES = 8;

    public ChartRecommendation recommend(List<ColumnMeta> columns, List<Map<String, Object>> rows) {
        ChartRecommendation rec = new ChartRecommendation();
        if (columns == null || columns.isEmpty()) {
            rec.setType("table");
            rec.setReason("No columns");
            return rec;
        }

        List<ColumnMeta> measures = new java.util.ArrayList<>();
        List<ColumnMeta> dimensions = new java.util.ArrayList<>();
        List<ColumnMeta> times = new java.util.ArrayList<>();
        for (ColumnMeta c : columns) {
            String category = classify(c);
            c.setCategory(category);
            switch (category) {
                case "measure" -> measures.add(c);
                case "time" -> times.add(c);
                default -> dimensions.add(c);
            }
        }

        int rowCount = rows == null ? 0 : rows.size();
        if (rowCount == 0 || columns.size() < 2 || measures.isEmpty()) {
            rec.setType("table");
            rec.setReason("Result is best shown as a table");
            return rec;
        }

        if (!times.isEmpty()) {
            ColumnMeta x = times.get(0);
            rec.setType("line");
            rec.setXField(x.getName());
            rec.setYFields(measures.stream().map(ColumnMeta::getName).toList());
            rec.setReason("Detected a time dimension and " + measures.size() + " measure(s) -> line chart");
            return rec;
        }

        if (!dimensions.isEmpty()) {
            ColumnMeta x = dimensions.get(0);
            rec.setXField(x.getName());
            rec.setYFields(measures.stream().map(ColumnMeta::getName).toList());
            if (dimensions.size() == 1 && measures.size() == 1 && rowCount >= 2 && rowCount <= PIE_MAX_SLICES) {
                rec.setType("pie");
                rec.setReason("Single category with one measure and few rows -> pie chart");
            } else {
                rec.setType("bar");
                rec.setReason("Category dimension with " + measures.size() + " measure(s) -> bar chart");
            }
            if (dimensions.size() >= 2) {
                rec.setSeriesField(dimensions.get(1).getName());
            }
            return rec;
        }

        rec.setType("table");
        rec.setReason("Result is best shown as a table");
        return rec;
    }

    private String classify(ColumnMeta c) {
        String type = c.getType() == null ? "" : c.getType().toUpperCase();
        if (type.contains("DATE") || type.contains("TIME") || type.contains("YEAR")) {
            return "time";
        }
        boolean numeric = type.contains("INT") || type.contains("DEC") || type.contains("NUM")
                || type.contains("DOUBLE") || type.contains("FLOAT") || type.contains("REAL")
                || type.contains("MONEY");
        if (numeric && !ID_LIKE.matcher(c.getName() == null ? "" : c.getName()).find()) {
            return "measure";
        }
        return "dimension";
    }
}
