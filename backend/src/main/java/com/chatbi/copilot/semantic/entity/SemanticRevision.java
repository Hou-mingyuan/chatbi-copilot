package com.chatbi.copilot.semantic.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("semantic_revision")
public class SemanticRevision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semanticId;
    private Long datasourceId;
    private Integer version;
    private String definitionType;
    private String snapshot;
    private Long changedBy;
    private String changeType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
