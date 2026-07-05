package com.chatbi.copilot.common;

import com.chatbi.copilot.config.LlmProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Application health and smoke-test status")
@RestController
@RequestMapping("/health")
public class HealthController {

    private final LlmProperties llmProperties;

    public HealthController(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Operation(summary = "Health check for local and Docker smoke tests")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("llmProvider", llmProperties.getProvider());
        data.put("llmConfigured", llmProperties.isConfigured());
        return ApiResponse.ok(data);
    }
}
