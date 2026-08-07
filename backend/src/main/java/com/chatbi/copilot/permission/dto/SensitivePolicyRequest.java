package com.chatbi.copilot.permission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class SensitivePolicyRequest {
    @Valid
    @NotNull
    private Set<ColumnRefRequest> columns = new LinkedHashSet<>();
}
