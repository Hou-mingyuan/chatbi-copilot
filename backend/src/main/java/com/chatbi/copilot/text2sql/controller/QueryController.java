package com.chatbi.copilot.text2sql.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.QueryResult;
import com.chatbi.copilot.text2sql.dto.RunSqlRequest;
import com.chatbi.copilot.text2sql.service.Text2SqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Query", description = "Ask questions in natural language and run guarded SQL")
@RestController
@RequestMapping("/query")
public class QueryController {

    private final Text2SqlService service;

    public QueryController(Text2SqlService service) {
        this.service = service;
    }

    @Operation(summary = "Ask a natural-language question (Text2SQL + execute + chart)")
    @PostMapping("/ask")
    public ApiResponse<QueryResult> ask(@Valid @RequestBody AskRequest req) {
        return ApiResponse.ok(service.ask(req));
    }

    @Operation(summary = "Run an edited/known SQL (guarded, read-only)")
    @PostMapping("/run")
    public ApiResponse<QueryResult> run(@Valid @RequestBody RunSqlRequest req) {
        return ApiResponse.ok(service.run(req.getDatasourceId(), req.getSql()));
    }
}
