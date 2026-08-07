package com.chatbi.copilot.text2sql.job;

import com.chatbi.copilot.text2sql.dto.QueryResult;

import java.time.LocalDateTime;

public record QueryJobVo(String id, Long datasourceId, String sessionId, String jobType,
                         String status, int progress, String message, Long resultQueryId,
                         String errorMessage, LocalDateTime createdAt, LocalDateTime startedAt,
                         LocalDateTime finishedAt, QueryResult result) {
}
