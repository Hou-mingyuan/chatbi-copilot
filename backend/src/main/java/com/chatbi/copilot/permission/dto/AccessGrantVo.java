package com.chatbi.copilot.permission.dto;

import java.util.Set;

public record AccessGrantVo(Long userId, Long datasourceId, boolean canQuery,
                            boolean canExport, boolean canManageSemantic,
                            Set<String> tables, Set<ColumnRefRequest> sensitiveColumns) {
}
