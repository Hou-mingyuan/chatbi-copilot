package com.chatbi.copilot.permission.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("datasource_acl")
public class DatasourceAcl {
    @TableId("user_id")
    private Long userId;
    private Long datasourceId;
    private Integer canQuery;
    private Integer canExport;
    private Integer canManageSemantic;
    private Long grantedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
