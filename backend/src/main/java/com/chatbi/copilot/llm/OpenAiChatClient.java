package com.chatbi.copilot.llm;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.LlmProperties;
import com.chatbi.copilot.llm.dto.ChatCompletionRequest;
import com.chatbi.copilot.llm.dto.ChatCompletionResponse;
import com.chatbi.copilot.llm.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI-compatible chat client. The full endpoint is computed from {@code baseUrl} to avoid
 * base/relative-path pitfalls, so any provider with a {@code /chat/completions} route works.
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
        this.chatEndpoint = URI.create(stripTrailingSlash(props.getBaseUrl()) + "/chat/completions");
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
        ChatCompletionRequest body = new ChatCompletionRequest();
        body.setModel(props.getModel());
        body.setMessages(messages);
        body.setTemperature(props.getTemperature());
        body.setMaxTokens(props.getMaxTokens());
        body.setStream(false);

        try {
            ChatCompletionResponse resp = restClient.post()
                    .uri(chatEndpoint)
                    .body(body)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = resp == null ? null : resp.firstContent();
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
