package com.chatbi.copilot.semantic.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.semantic.dto.SemanticModelReq;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.service.SemanticService;
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

import java.util.List;

@Tag(name = "Semantic layer", description = "Business aliases and descriptions for tables/columns to boost Text2SQL accuracy")
@RestController
@RequestMapping("/semantic")
public class SemanticController {

    private final SemanticService service;

    public SemanticController(SemanticService service) {
        this.service = service;
    }

    @Operation(summary = "List semantic entries for a datasource")
    @GetMapping
    public ApiResponse<List<SemanticModel>> list(@RequestParam Long datasourceId) {
        return ApiResponse.ok(service.list(datasourceId));
    }

    @Operation(summary = "Create a semantic entry")
    @PostMapping
    public ApiResponse<SemanticModel> create(@Valid @RequestBody SemanticModelReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Update a semantic entry")
    @PutMapping("/{id}")
    public ApiResponse<SemanticModel> update(@PathVariable Long id, @Valid @RequestBody SemanticModelReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @Operation(summary = "Delete a semantic entry")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
