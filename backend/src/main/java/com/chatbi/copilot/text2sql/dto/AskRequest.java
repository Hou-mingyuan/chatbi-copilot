package com.chatbi.copilot.text2sql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AskRequest {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "question is required")
    @Size(max = 1000, message = "question is too long")
    private String question;

    /** If true, only generate SQL and skip execution (dry run). */
    private boolean previewOnly = false;

    @Size(max = 36, message = "sessionId is invalid")
    private String sessionId;

    private boolean confirmRisk;
}
