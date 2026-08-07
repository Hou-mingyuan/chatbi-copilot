package com.chatbi.copilot.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ColumnRefRequest(
        @NotBlank @Size(max = 128) String table,
        @NotBlank @Size(max = 128) String column
) {
}
