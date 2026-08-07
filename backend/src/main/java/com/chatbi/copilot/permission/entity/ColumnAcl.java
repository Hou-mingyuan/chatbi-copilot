package com.chatbi.copilot.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("column_acl")
public class ColumnAcl {
    @TableId("user_id")
    private Long userId;
    private Long datasourceId;
    private String tableName;
    private String columnName;
    private Long grantedBy;
    private LocalDateTime createdAt;
}
