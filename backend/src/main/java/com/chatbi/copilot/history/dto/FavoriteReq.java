package com.chatbi.copilot.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteReq {

    @NotNull(message = "datasourceId is required")
    private Long datasourceId;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "question is required")
    private String question;

    private String sql;
}
