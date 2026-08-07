package com.chatbi.copilot.semantic.service;

import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.semantic.entity.SemanticModel;

import java.util.List;

public record SemanticContext(SchemaInfo schema, List<SemanticModel> definitions,
                              int totalTables, int selectedTables,
                              int totalColumns, int selectedColumns,
                              boolean truncated) {
}
