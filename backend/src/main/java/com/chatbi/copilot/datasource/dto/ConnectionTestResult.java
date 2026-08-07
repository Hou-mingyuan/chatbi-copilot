package com.chatbi.copilot.datasource.dto;

import java.util.List;

public record ConnectionTestResult(boolean connected, boolean readOnlyVerified,
                                   String databaseProduct, String databaseVersion,
                                   List<String> evidence) {
}
