package com.chatbi.copilot.common;

import com.chatbi.copilot.config.LlmProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Application health and smoke-test status")
@RestController
@RequestMapping("/health")
public class HealthController {

    private final LlmProperties llmProperties;
    private final JdbcTemplate jdbcTemplate;

    public HealthController(LlmProperties llmProperties, JdbcTemplate jdbcTemplate) {
        this.llmProperties = llmProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "Authenticated health details")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return readiness();
    }

    @Operation(summary = "Unauthenticated process liveness probe")
    @GetMapping("/live")
    public ApiResponse<Map<String, Object>> liveness() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }

    @Operation(summary = "Unauthenticated metadata-store readiness probe")
    @GetMapping("/ready")
    public ApiResponse<Map<String, Object>> readiness() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("llmProvider", llmProperties.getProvider());
        data.put("llmConfigured", llmProperties.isConfigured());
        return ApiResponse.ok(data);
    }
}
