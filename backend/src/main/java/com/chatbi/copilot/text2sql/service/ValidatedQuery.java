package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.permission.ColumnRef;

import java.util.Set;

public record ValidatedQuery(String sql, Set<String> tables, Set<ColumnRef> columns,
                             Set<String> projectionWildcards, Set<String> functions) {
}
