package com.chatbi.copilot.text2sql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import jakarta.validation.constraints.Size;

@Data
public class RunSqlRequest {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "sql is required")
    @Size(max = 50000, message = "sql is too long")
    private String sql;

    private boolean confirmRisk;

    @Size(max = 36, message = "sessionId is invalid")
    private String sessionId;
}
