package com.chatbi.copilot.chart;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggested visualization for a result set. The frontend can always override the type.
 */
@Data
public class ChartRecommendation {

    /** bar | line | pie | table */
    private String type = "table";

    /** Category/dimension field (x-axis). */
    private String xField;

    /** Numeric measure field(s) (y-axis / values). */
    private List<String> yFields = new ArrayList<>();

    /** Optional series/group field for multi-series charts. */
    private String seriesField;

    private String title;

    private String reason;
}
