package com.chatbi.copilot.permission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class AccessGrantRequest {
    private boolean canQuery;
    private boolean canExport;
    private boolean canManageSemantic;
    @NotNull
    private Set<String> tables = new LinkedHashSet<>();
    @Valid
    @NotNull
    private Set<ColumnRefRequest> sensitiveColumns = new LinkedHashSet<>();
}
