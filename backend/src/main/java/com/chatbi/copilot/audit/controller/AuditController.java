package com.chatbi.copilot.audit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chatbi.copilot.audit.entity.AuditEvent;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IPage<AuditEvent>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.page(page, size));
    }
}
