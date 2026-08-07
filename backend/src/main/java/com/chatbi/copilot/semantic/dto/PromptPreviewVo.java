package com.chatbi.copilot.semantic.dto;

import java.util.List;

public record PromptPreviewVo(List<String> selectedTables, List<String> semanticDefinitions,
                              int selectedColumns, int totalTables, int totalColumns,
                              boolean truncated, int renderedCharacters, String renderedContext) {
}
