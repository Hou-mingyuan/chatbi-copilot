package com.chatbi.copilot.history.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.history.entity.QuerySession;
import com.chatbi.copilot.history.service.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sessions")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<QuerySession>> list(@RequestParam(required = false) Long datasourceId) {
        return ApiResponse.ok(service.list(datasourceId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
