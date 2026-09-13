package com.chatbi.copilot.chart;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggested visualization for a result set. The frontend can always override the type.
 *
 * <p>Jackson 2.19 (Spring Boot 3.5) splits a field-level {@code @JsonProperty} and its
 * Lombok-generated getter into two properties when the bean-decapitalized implicit getter name
 * differs ("xField" vs "xfield"), so the getters for the aliased fields are declared explicitly
 * to keep a single merged property per field. Lombok skips generating them.
 */
@Data
public class ChartRecommendation {

    /** bar | line | pie | table */
    private String type = "table";

    /** Category/dimension field (x-axis). */
    @JsonProperty("xField")
    @JsonAlias("xfield")
    private String xField;

    /** Numeric measure field(s) (y-axis / values). */
    @JsonProperty("yFields")
    @JsonAlias("yfields")
    private List<String> yFields = new ArrayList<>();

    /** Optional series/group field for multi-series charts. */
    @JsonProperty("seriesField")
    @JsonAlias("seriesfield")
    private String seriesField;

    private String title;

    private String reason;

    @JsonProperty("xField")
    public String getXField() {
        return xField;
    }

    @JsonProperty("yFields")
    public List<String> getYFields() {
        return yFields;
    }

    @JsonProperty("seriesField")
    public String getSeriesField() {
        return seriesField;
    }
}
