package com.chatbi.copilot.semantic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SemanticModelReq {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "tableName is required")
    private String tableName;

    /** Leave blank for a table-level entry. */
    private String columnName;

    private String businessAlias;

    private String description;
}
