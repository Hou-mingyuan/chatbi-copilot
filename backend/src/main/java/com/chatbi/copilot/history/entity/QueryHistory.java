package com.chatbi.copilot.history.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("query_history")
public class QueryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasourceId;

    private String question;

    private String generatedSql;

    /** 1 = success, 0 = failure. */
    private Integer success;

    private String errorMsg;

    private Integer rowCount;

    private Long elapsedMs;

    private String chartType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
