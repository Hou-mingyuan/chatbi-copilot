package com.chatbi.copilot.llm;

import com.chatbi.copilot.config.LlmProperties;
import com.chatbi.copilot.llm.dto.ChatCompletionRequest;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.llm.dto.ResponsesRequest;
import com.chatbi.copilot.llm.dto.ResponsesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gpt5UsesCompletionTokenAndReasoningParameters() throws Exception {
        LlmProperties props = properties("openai", "gpt-5.6-sol");
        props.setReasoningEffort("none");

        ChatCompletionRequest request = new OpenAiChatClient(props)
                .buildRequest(List.of(ChatMessage.user("hello")));
        String json = objectMapper.writeValueAsString(request);

        assertThat(request.getTemperature()).isNull();
        assertThat(request.getMaxTokens()).isNull();
        assertThat(request.getMaxCompletionTokens()).isEqualTo(2048);
        assertThat(request.getReasoningEffort()).isEqualTo("none");
        assertThat(json).contains("\"max_completion_tokens\":2048", "\"reasoning_effort\":\"none\"");
        assertThat(json).doesNotContain("\"max_tokens\"", "\"temperature\"");
    }

    @Test
    void otherCompatibleModelsKeepLegacyParameters() throws Exception {
        LlmProperties props = properties("deepseek", "deepseek-chat");

        ChatCompletionRequest request = new OpenAiChatClient(props)
                .buildRequest(List.of(ChatMessage.user("hello")));
        String json = objectMapper.writeValueAsString(request);

        assertThat(request.getTemperature()).isEqualTo(0.0);
        assertThat(request.getMaxTokens()).isEqualTo(2048);
        assertThat(request.getMaxCompletionTokens()).isNull();
        assertThat(request.getReasoningEffort()).isNull();
        assertThat(json).contains("\"max_tokens\":2048", "\"temperature\":0.0");
        assertThat(json).doesNotContain("max_completion_tokens", "reasoning_effort");
    }

    @Test
    void responsesRequestUsesOutputTokenFieldAndOptionalGpt5Reasoning() throws Exception {
        LlmProperties props = properties("openai", "gpt-5.6-sol");
        props.setApiStyle("responses");

        ResponsesRequest request = new OpenAiChatClient(props)
                .buildResponsesRequest(List.of(ChatMessage.system("rules"), ChatMessage.user("question")));
        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"max_output_tokens\":2048", "\"reasoning\":{\"effort\":\"none\"}");
        assertThat(json).doesNotContain("max_tokens", "max_completion_tokens", "temperature");
    }

    @Test
    void responsesResponseExtractsOnlyAssistantOutputText() throws Exception {
        String json = """
                {"status":"completed","output":[
                  {"type":"reasoning","content":[]},
                  {"type":"message","content":[{"type":"output_text","text":"{\\\"sql\\\":\\\"SELECT 1\\\"}"}]}
                ]}
                """;

        ResponsesResponse response = objectMapper.readValue(json, ResponsesResponse.class);

        assertThat(response.text()).isEqualTo("{\"sql\":\"SELECT 1\"}");
    }

    private static LlmProperties properties(String provider, String model) {
        LlmProperties props = new LlmProperties();
        props.setProvider(provider);
        props.setBaseUrl("https://example.test/v1");
        props.setModel(model);
        props.setApiKey("test-key");
        return props;
    }
}
