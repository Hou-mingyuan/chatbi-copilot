package com.chatbi.copilot.semantic.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.semantic.dto.SemanticModelReq;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.service.SemanticService;
import com.chatbi.copilot.semantic.entity.SemanticRevision;
import com.chatbi.copilot.semantic.dto.PromptPreviewVo;
import com.chatbi.copilot.semantic.service.SemanticContext;
import com.chatbi.copilot.semantic.service.SemanticContextRetriever;
import com.chatbi.copilot.datasource.service.DataSourceService;
import com.chatbi.copilot.text2sql.service.PromptBuilder;
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
    private final SemanticContextRetriever contextRetriever;
    private final DataSourceService dataSourceService;
    private final PromptBuilder promptBuilder;

    public SemanticController(SemanticService service, SemanticContextRetriever contextRetriever,
                              DataSourceService dataSourceService, PromptBuilder promptBuilder) {
        this.service = service;
        this.contextRetriever = contextRetriever;
        this.dataSourceService = dataSourceService;
        this.promptBuilder = promptBuilder;
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

    @Operation(summary = "List version history for a semantic definition")
    @GetMapping("/{id}/revisions")
    public ApiResponse<List<SemanticRevision>> revisions(@PathVariable Long id) {
        return ApiResponse.ok(service.revisions(id));
    }

    @Operation(summary = "Preview the bounded schema and semantic context injected into the model")
    @GetMapping("/prompt-preview")
    public ApiResponse<PromptPreviewVo> promptPreview(@RequestParam Long datasourceId,
                                                      @RequestParam String question) {
        SemanticContext context = contextRetriever.retrieve(
                dataSourceService.getSchema(datasourceId, false), question);
        String rendered = promptBuilder.renderSchema(context.schema()) + "\n"
                + promptBuilder.renderSemantic(context.definitions());
        List<String> tables = context.schema().getTables().stream().map(t -> t.getName()).toList();
        List<String> definitions = context.definitions().stream()
                .map(model -> model.getDefinitionType() + ":" + model.getBusinessAlias()).toList();
        return ApiResponse.ok(new PromptPreviewVo(tables, definitions, context.selectedColumns(),
                context.totalTables(), context.totalColumns(), context.truncated(), rendered.length(), rendered));
    }
}
