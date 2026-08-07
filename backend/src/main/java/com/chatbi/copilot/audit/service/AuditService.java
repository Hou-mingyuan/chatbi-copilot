package com.chatbi.copilot.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.copilot.audit.entity.AuditEvent;
import com.chatbi.copilot.audit.mapper.AuditEventMapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.RequestIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditService {
    private final AuditEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUser;

    public AuditService(AuditEventMapper mapper, ObjectMapper objectMapper, CurrentUserService currentUser) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    public void record(String action, String resourceType, Object resourceId,
                       String outcome, Map<String, ?> details) {
        AppUserPrincipal actor = null;
        try {
            actor = currentUser.required();
        } catch (RuntimeException ignored) {
            // Login failures and readiness checks may be unauthenticated.
        }
        AuditEvent event = new AuditEvent();
        event.setActorUserId(actor == null ? null : actor.userId());
        event.setRequestId(requestId());
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        event.setOutcome(outcome);
        event.setDetails(toJson(details));
        mapper.insert(event);
    }

    public IPage<AuditEvent> page(long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        return mapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getId));
    }

    private String requestId() {
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        return value == null ? "system" : value;
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details could not be serialized", e);
        }
    }
}
