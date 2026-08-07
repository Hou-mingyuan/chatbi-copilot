package com.chatbi.copilot.history.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteReq {

    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "queryId is required")
    private Long queryId;
}
