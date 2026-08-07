package com.chatbi.copilot.text2sql.plan;

import java.util.List;

public record QueryRisk(String level, long estimatedRows, boolean fullScan,
                        boolean requiresConfirmation, boolean blocked,
                        List<String> reasons, List<String> planSummary) {
}
