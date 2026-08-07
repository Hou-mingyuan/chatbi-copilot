package com.chatbi.copilot.permission.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.permission.dto.AccessGrantRequest;
import com.chatbi.copilot.permission.dto.AccessGrantVo;
import com.chatbi.copilot.permission.dto.ColumnRefRequest;
import com.chatbi.copilot.permission.dto.SensitivePolicyRequest;
import com.chatbi.copilot.permission.service.AdminPermissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {
    private final AdminPermissionService service;

    public AdminPermissionController(AdminPermissionService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/datasources/{datasourceId}")
    public ApiResponse<AccessGrantVo> getGrant(@PathVariable Long userId,
                                               @PathVariable Long datasourceId) {
        return ApiResponse.ok(service.getGrant(userId, datasourceId));
    }

    @PutMapping("/users/{userId}/datasources/{datasourceId}")
    public ApiResponse<AccessGrantVo> replaceGrant(@PathVariable Long userId,
                                                   @PathVariable Long datasourceId,
                                                   @Valid @RequestBody AccessGrantRequest request) {
        return ApiResponse.ok(service.replaceGrant(userId, datasourceId, request));
    }

    @GetMapping("/datasources/{datasourceId}/sensitive-columns")
    public ApiResponse<Set<ColumnRefRequest>> getSensitive(@PathVariable Long datasourceId) {
        return ApiResponse.ok(service.getSensitivePolicies(datasourceId));
    }

    @PutMapping("/datasources/{datasourceId}/sensitive-columns")
    public ApiResponse<Set<ColumnRefRequest>> replaceSensitive(@PathVariable Long datasourceId,
                                                               @Valid @RequestBody SensitivePolicyRequest request) {
        return ApiResponse.ok(service.replaceSensitivePolicies(datasourceId, request));
    }
}
