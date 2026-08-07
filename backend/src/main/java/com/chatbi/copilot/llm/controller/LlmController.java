package com.chatbi.copilot.llm.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.config.LlmProperties;
import com.chatbi.copilot.llm.LlmClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "LLM", description = "LLM provider status")
@RestController
@RequestMapping("/llm")
public class LlmController {

    private final LlmProperties props;
    private final LlmClient client;

    public LlmController(LlmProperties props, LlmClient client) {
        this.props = props;
        this.client = client;
    }

    @Operation(summary = "Current LLM configuration status (never returns the API key)")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", props.getProvider());
        status.put("model", props.getModel());
        status.put("apiStyle", props.getApiStyle());
        status.put("baseUrl", props.getBaseUrl());
        status.put("hasApiKey", props.getApiKey() != null && !props.getApiKey().isBlank());
        status.put("mockMode", "mock".equalsIgnoreCase(String.valueOf(props.getProvider()).trim()));
        status.put("configured", client.isConfigured());
        return ApiResponse.ok(status);
    }
}
