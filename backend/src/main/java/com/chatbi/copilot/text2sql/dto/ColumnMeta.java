package com.chatbi.copilot.text2sql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMeta {
    private String name;
    /** JDBC type name, e.g. VARCHAR, DECIMAL, TIMESTAMP. */
    private String type;
    /** measure | time | dimension - filled by the chart recommender. */
    private String category;
}
