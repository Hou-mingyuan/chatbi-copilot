package com.chatbi.copilot.text2sql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AskRequest {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "question is required")
    private String question;

    /** Prior turns for multi-turn follow-ups (optional). */
    private List<HistoryTurn> history = new ArrayList<>();

    /** If true, only generate SQL and skip execution (dry run). */
    private boolean previewOnly = false;
}
