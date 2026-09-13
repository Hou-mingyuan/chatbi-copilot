package com.chatbi.copilot.llm;

import com.chatbi.copilot.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal OpenAI-compatible {@code /embeddings} client used for semantic schema recall.
 * Returns an empty list on any failure so callers can fall back to lexical scoring —
 * embedding recall is an enhancement, never a hard dependency.
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public EmbeddingClient(LlmProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getEmbeddings().isEnabled()
                && !properties.getBaseUrl().isBlank()
                && properties.getEmbeddings().getModel() != null
                && !properties.getEmbeddings().getModel().isBlank();
    }

    /** Embeds the given inputs; returns one vector per input, or an empty list on failure. */
    public List<double[]> embed(List<String> inputs) {
        if (!isEnabled() || inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        try {
            String endpoint = properties.getBaseUrl().replaceAll("/$", "") + "/embeddings";
            Map<String, Object> payload = Map.of(
                    "model", properties.getEmbeddings().getModel(),
                    "input", inputs);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.getEmbeddings().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Embedding request failed with status {}: {}", response.statusCode(),
                        truncate(response.body()));
                return List.of();
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<double[]> vectors = new ArrayList<>();
            for (JsonNode entry : data) {
                JsonNode embedding = entry.path("embedding");
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (Exception e) {
            log.warn("Embedding request failed, falling back to lexical recall: {}", e.getMessage());
            return List.of();
        }
    }

    /** Cosine similarity; returns 0 when either vector is degenerate. */
    public static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String truncate(String value) {
        return value == null ? "" : value.substring(0, Math.min(200, value.length()));
    }
}
