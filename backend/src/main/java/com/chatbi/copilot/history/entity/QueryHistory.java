package com.chatbi.copilot.history.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
@TableName("query_history")
public class QueryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private Long datasourceId;

    private String question;

    private String generatedSql;

    private String editedSql;

    private String status;

    /** 1 = success, 0 = failure. */
    private Integer success;

    private String errorMsg;

    private String explanation;

    private String clarification;

    private Integer rowCount;

    private Long elapsedMs;

    private String chartType;

    private String riskLevel;

    @JsonIgnore
    private String resultSnapshot;

    private String resultHash;

    @JsonIgnore
    private String referencedResources;

    private String summary;

    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
