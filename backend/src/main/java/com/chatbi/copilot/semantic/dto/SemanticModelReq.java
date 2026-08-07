package com.chatbi.copilot.semantic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SemanticModelReq {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "definitionType is required")
    private String definitionType = "COLUMN";

    @NotBlank(message = "tableName is required")
    @Size(max = 128, message = "tableName is too long")
    private String tableName;

    /** Leave blank for a table-level entry. */
    @Size(max = 128, message = "columnName is too long")
    private String columnName;

    @Size(max = 255, message = "businessAlias is too long")
    private String businessAlias;

    @Size(max = 1000, message = "description is too long")
    private String description;

    @Size(max = 1000, message = "metricExpression is too long")
    private String metricExpression;
    @Size(max = 32)
    private String aggregation;
    @Size(max = 64)
    private String unit;
    @Size(max = 32)
    private String timeGrain;
    @Size(max = 255)
    private String enumValue;
    @Size(max = 255)
    private String enumLabel;
    @Size(max = 128)
    private String relatedTable;
    @Size(max = 128)
    private String relatedColumn;
    @Size(max = 32)
    private String joinType;
    private boolean active = true;
}
