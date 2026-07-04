package com.chatbi.copilot.datasource.dto;

import lombok.Data;

@Data
public class ColumnSchema {
    private String name;
    private String dataType;
    private String comment;
    private boolean nullable;
    private boolean primaryKey;

    /** Business alias from the semantic layer, if configured. */
    private String businessAlias;
    /** Business description from the semantic layer, if configured. */
    private String businessDescription;
}
