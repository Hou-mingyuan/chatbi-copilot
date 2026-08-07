package com.chatbi.copilot.datasource.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.datasource.dto.DataSourceReq;
import com.chatbi.copilot.datasource.dto.DataSourceVo;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.ConnectionTestResult;
import com.chatbi.copilot.datasource.service.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Tag(name = "Datasources", description = "Manage target database connections and read their schema")
@RestController
@RequestMapping("/datasources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @Operation(summary = "List datasources")
    @GetMapping
    public ApiResponse<List<DataSourceVo>> list() {
        return ApiResponse.ok(service.list());
    }

    @Operation(summary = "Get a datasource")
    @GetMapping("/{id}")
    public ApiResponse<DataSourceVo> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Create a datasource")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DataSourceVo> create(@Valid @RequestBody DataSourceReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Update a datasource")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DataSourceVo> update(@PathVariable Long id, @Valid @RequestBody DataSourceReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Delete a datasource")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "Test an unsaved connection")
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ConnectionTestResult> test(@Valid @RequestBody DataSourceReq req) {
        return ApiResponse.ok(service.testConnection(req));
    }

    @Operation(summary = "Test a saved connection")
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ConnectionTestResult> testById(@PathVariable Long id) {
        return ApiResponse.ok(service.testConnectionById(id));
    }

    @Operation(summary = "Get schema (tables/columns/comments) enriched with the semantic layer")
    @GetMapping("/{id}/schema")
    public ApiResponse<SchemaInfo> schema(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(service.getSchema(id, refresh));
    }
}
