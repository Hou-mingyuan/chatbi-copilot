package com.chatbi.copilot.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_event")
public class AuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private String requestId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String details;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
