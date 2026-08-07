package com.chatbi.copilot.text2sql.plan;

import java.util.List;

public record PlanEstimate(long estimatedRows, boolean fullScan, List<String> summary) {
}
