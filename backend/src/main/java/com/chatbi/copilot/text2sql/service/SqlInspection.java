package com.chatbi.copilot.text2sql.service;

import java.util.Map;
import java.util.Set;

public record SqlInspection(
        String sql,
        Set<String> tables,
        Set<RawColumnReference> columns,
        Map<String, String> aliases,
        Set<String> projectionWildcards,
        Set<String> functions
) {
}
