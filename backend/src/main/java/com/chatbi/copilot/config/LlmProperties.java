package com.chatbi.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM connection settings. OpenAI-compatible Chat Completions and Responses endpoints are supported,
 * so you can switch between DeepSeek / OpenAI / Qwen(DashScope compatible mode) / Ollama
 * purely through configuration.
 */
@Data
@ConfigurationProperties(prefix = "chatbi.llm")
public class LlmProperties {

    /** Informational label only, e.g. deepseek / openai / qwen / ollama. */
    private String provider = "deepseek";

    /** Base URL of the OpenAI-compatible API, e.g. https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API key. Injected from env var LLM_API_KEY; may be blank for local Ollama. */
    private String apiKey = "";

    /** Model name, e.g. deepseek-chat / gpt-4o-mini / qwen-plus / llama3.1 */
    private String model = "deepseek-chat";

    /** API wire format: chat-completions or responses. */
    private String apiStyle = "chat-completions";

    /** Low temperature keeps SQL generation deterministic. */
    private Double temperature = 0.0;

    private Integer maxTokens = 2048;

    /** OpenAI reasoning effort. Applied only to OpenAI GPT-5 family models. */
    private String reasoningEffort = "none";

    private Integer timeoutSeconds = 60;

    /** Embedding endpoint settings for semantic schema recall. Off by default: lexical recall works without it. */
    private Embeddings embeddings = new Embeddings();

    public boolean isConfigured() {
        if (provider != null && "mock".equalsIgnoreCase(provider.trim())) {
            return true;
        }
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            return false;
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return true;
        }
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        String normalizedBaseUrl = baseUrl.trim().toLowerCase();
        return "ollama".equals(normalizedProvider)
                || normalizedBaseUrl.contains("localhost")
                || normalizedBaseUrl.contains("127.0.0.1")
                || normalizedBaseUrl.contains("host.docker.internal");
    }

    @Data
    public static class Embeddings {
        /** Master switch; when off (or on any client failure) schema recall falls back to lexical scoring. */
        private boolean enabled = false;
        /** OpenAI-compatible embeddings model, e.g. text-embedding-3-small. */
        private String model = "text-embedding-3-small";
        private int timeoutSeconds = 20;
    }
}
