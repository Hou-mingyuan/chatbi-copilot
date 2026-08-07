package com.chatbi.copilot.text2sql.job;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("query_job")
public class QueryJob {
    @TableId
    private String id;
    private Long userId;
    private Long datasourceId;
    private String sessionId;
    private String jobType;
    @JsonIgnore
    private String requestJson;
    private String status;
    private Integer progress;
    private String message;
    private Long resultQueryId;
    private String errorMessage;
    private Integer cancelRequested;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
