package com.chatbi.copilot.llm;

import com.chatbi.copilot.llm.dto.ChatMessage;

import java.util.List;

/**
 * Abstraction over an OpenAI-compatible chat model. Implementations talk to any provider that
 * exposes a {@code /chat/completions} endpoint (DeepSeek, OpenAI, Qwen compatible mode, Ollama...).
 */
public interface LlmClient {

    /** Send a chat conversation and return the assistant's text reply. */
    String chat(List<ChatMessage> messages);

    /** Whether an endpoint/model is configured. */
    boolean isConfigured();
}
