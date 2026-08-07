package com.chatbi.copilot.permission;

import java.util.Locale;

public record ColumnRef(String table, String column) {
    public ColumnRef {
        table = normalize(table);
        column = normalize(column);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
