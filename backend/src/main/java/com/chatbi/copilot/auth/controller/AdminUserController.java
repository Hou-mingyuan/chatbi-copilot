package com.chatbi.copilot.auth.controller;

import com.chatbi.copilot.auth.dto.UserCreateRequest;
import com.chatbi.copilot.auth.dto.UserUpdateRequest;
import com.chatbi.copilot.auth.dto.UserVo;
import com.chatbi.copilot.auth.service.AdminUserService;
import com.chatbi.copilot.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<UserVo>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<UserVo> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserVo> update(@PathVariable Long id,
                                      @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }
}
