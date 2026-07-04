package com.chatbi.copilot.datasource.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SchemaInfo {
    private Long datasourceId;
    private String databaseName;
    private String dbType;
    private List<TableSchema> tables = new ArrayList<>();
}
