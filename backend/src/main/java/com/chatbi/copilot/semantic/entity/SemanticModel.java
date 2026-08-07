package com.chatbi.copilot.semantic.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Business semantics for a table or column: a friendly alias and a description that get
 * injected into the LLM prompt to improve Text2SQL accuracy. A null {@code columnName}
 * means the entry describes the table itself.
 */
@Data
@TableName("semantic_model")
public class SemanticModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasourceId;

    private String definitionType;

    private String tableName;

    /** null / blank => table-level entry. */
    private String columnName;

    private String businessAlias;

    private String description;

    private String metricExpression;
    private String aggregation;
    private String unit;
    private String timeGrain;
    private String enumValue;
    private String enumLabel;
    private String relatedTable;
    private String relatedColumn;
    private String joinType;
    private Integer version;
    private Integer active;
    private Long createdBy;
    private Long updatedBy;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
