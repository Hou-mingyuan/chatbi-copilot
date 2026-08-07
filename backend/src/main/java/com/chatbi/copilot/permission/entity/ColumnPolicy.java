package com.chatbi.copilot.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("column_policy")
public class ColumnPolicy {
    @TableId("datasource_id")
    private Long datasourceId;
    private String tableName;
    private String columnName;
    private Integer sensitive;
    private String label;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
