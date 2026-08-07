package com.chatbi.copilot.history.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History", description = "Query history")
@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @Operation(summary = "Page query history")
    @GetMapping
    public ApiResponse<IPage<QueryHistory>> page(@RequestParam(required = false) Long datasourceId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.page(datasourceId, page, size));
    }

    @Operation(summary = "Restore one authorized query result from its immutable snapshot")
    @GetMapping("/{id}/result")
    public ApiResponse<QueryResult> result(@PathVariable Long id) {
        return ApiResponse.ok(service.result(id, Capability.QUERY));
    }

    @Operation(summary = "Delete one history entry")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "Clear history (optionally by datasource)")
    @DeleteMapping
    public ApiResponse<Void> clear(@RequestParam(required = false) Long datasourceId) {
        service.clear(datasourceId);
        return ApiResponse.ok();
    }
}
