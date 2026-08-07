package com.chatbi.copilot.text2sql.service;

import java.util.Locale;

public record RawColumnReference(String qualifier, String column) {
    public RawColumnReference {
        qualifier = normalize(qualifier);
        column = normalize(column);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
