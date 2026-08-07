package com.chatbi.copilot.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_acl")
public class TableAcl {
    @TableId("user_id")
    private Long userId;
    private Long datasourceId;
    private String tableName;
    private Long grantedBy;
    private LocalDateTime createdAt;
}
