package com.chatbi.copilot.datasource.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableSchema {
    private String name;
    private String comment;
    private List<ColumnSchema> columns = new ArrayList<>();

    /** Business alias from the semantic layer, if configured. */
    private String businessAlias;
    /** Business description from the semantic layer, if configured. */
    private String businessDescription;
}
