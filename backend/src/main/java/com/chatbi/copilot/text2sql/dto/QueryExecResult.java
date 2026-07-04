package com.chatbi.copilot.text2sql.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Raw execution output produced by the SQL executor.
 */
@Data
public class QueryExecResult {
    private List<ColumnMeta> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private int rowCount;
    private boolean truncated;
    private long elapsedMs;
}
