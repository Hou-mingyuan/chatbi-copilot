package com.chatbi.copilot.text2sql.job;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.text2sql.dto.AskRequest;
import com.chatbi.copilot.text2sql.dto.RunSqlRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/query/jobs")
public class QueryJobController {
    private final QueryJobService service;

    public QueryJobController(QueryJobService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<QueryJobVo>> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.ok(service.createAsk(request)));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<QueryJobVo>> run(@Valid @RequestBody RunSqlRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.ok(service.createRun(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<QueryJobVo> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping
    public ApiResponse<List<QueryJobVo>> list(@RequestParam(required = false) Long datasourceId) {
        return ApiResponse.ok(service.list(datasourceId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<QueryJobVo> cancel(@PathVariable String id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<QueryJobVo>> retry(@PathVariable String id,
                                                         @RequestParam(defaultValue = "false") boolean confirmRisk) {
        return ResponseEntity.accepted().body(ApiResponse.ok(service.retry(id, confirmRisk)));
    }
}
