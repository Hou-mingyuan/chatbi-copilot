package com.chatbi.copilot.text2sql.dto;

import java.util.List;

public record ResultSummary(String text, List<InsightFact> facts) {
}
