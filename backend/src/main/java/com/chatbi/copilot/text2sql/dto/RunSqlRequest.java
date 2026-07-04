package com.chatbi.copilot.text2sql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunSqlRequest {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "sql is required")
    private String sql;
}
