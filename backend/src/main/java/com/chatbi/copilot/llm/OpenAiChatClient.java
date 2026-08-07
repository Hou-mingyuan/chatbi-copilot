package com.chatbi.copilot.llm;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.LlmProperties;
import com.chatbi.copilot.llm.dto.ChatCompletionRequest;
import com.chatbi.copilot.llm.dto.ChatCompletionResponse;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.llm.dto.ResponsesRequest;
import com.chatbi.copilot.llm.dto.ResponsesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible client for both Chat Completions and Responses wire formats.
 */
@Slf4j
public class OpenAiChatClient implements LlmClient {

    private final LlmProperties props;
    private final RestClient restClient;
    private final URI chatEndpoint;

    public OpenAiChatClient(LlmProperties props) {
        this.props = props;

        int timeoutSeconds = props.getTimeoutSeconds() == null ? 60 : props.getTimeoutSeconds();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey().trim());
        }
        this.restClient = builder.build();
        this.chatEndpoint = URI.create(stripTrailingSlash(props.getBaseUrl())
                + (usesResponsesApi() ? "/responses" : "/chat/completions"));
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        if (!isConfigured()) {
            throw new BusinessException("LLM is not configured. Set LLM_BASE_URL / LLM_MODEL (and LLM_API_KEY).");
        }
        try {
            String content = usesResponsesApi() ? callResponses(messages) : callChatCompletions(messages);
            if (content == null || content.isBlank()) {
                throw new BusinessException("LLM returned an empty response");
            }
            return content;
        } catch (RestClientResponseException e) {
            log.error("LLM API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("LLM API error " + e.getStatusCode().value() + ": "
                    + truncate(e.getResponseBodyAsString(), 300));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw new BusinessException("LLM call failed: " + e.getMessage());
        }
    }

    private String callChatCompletions(List<ChatMessage> messages) {
        ChatCompletionResponse response = restClient.post()
                .uri(chatEndpoint)
                .body(buildRequest(messages))
                .retrieve()
                .body(ChatCompletionResponse.class);
        return response == null ? null : response.firstContent();
    }

    private String callResponses(List<ChatMessage> messages) {
        ResponsesResponse response = restClient.post()
                .uri(chatEndpoint)
                .body(buildResponsesRequest(messages))
                .retrieve()
                .body(ResponsesResponse.class);
        return response == null ? null : response.text();
    }

    ChatCompletionRequest buildRequest(List<ChatMessage> messages) {
        ChatCompletionRequest body = new ChatCompletionRequest();
        body.setModel(props.getModel());
        body.setMessages(messages);
        if (usesOpenAiGpt5Parameters()) {
            body.setMaxCompletionTokens(props.getMaxTokens());
            String effort = props.getReasoningEffort();
            body.setReasoningEffort(effort == null || effort.isBlank() ? "none" : effort.trim());
        } else {
            body.setTemperature(props.getTemperature());
            body.setMaxTokens(props.getMaxTokens());
        }
        body.setStream(false);
        return body;
    }

    ResponsesRequest buildResponsesRequest(List<ChatMessage> messages) {
        ResponsesRequest body = new ResponsesRequest();
        body.setModel(props.getModel());
        body.setInput(messages);
        body.setMaxOutputTokens(props.getMaxTokens());
        if (usesOpenAiGpt5Parameters()) {
            String effort = props.getReasoningEffort();
            body.setReasoning(Map.of("effort", effort == null || effort.isBlank() ? "none" : effort.trim()));
        }
        body.setStream(false);
        return body;
    }

    private boolean usesResponsesApi() {
        String style = props.getApiStyle() == null ? "" : props.getApiStyle().trim();
        return "responses".equalsIgnoreCase(style) || "response".equalsIgnoreCase(style);
    }

    private boolean usesOpenAiGpt5Parameters() {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim();
        String model = props.getModel() == null ? "" : props.getModel().trim();
        return "openai".equalsIgnoreCase(provider) && model.toLowerCase().startsWith("gpt-5");
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
