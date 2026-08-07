package com.chatbi.copilot.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsesResponse {

    private String status;
    private List<OutputItem> output;

    @JsonProperty("output_text")
    private String outputText;

    public String text() {
        if (outputText != null && !outputText.isBlank()) {
            return outputText;
        }
        if (output == null) {
            return null;
        }
        String joined = output.stream()
                .filter(Objects::nonNull)
                .filter(item -> "message".equals(item.getType()))
                .flatMap(item -> item.getContent() == null ? java.util.stream.Stream.empty() : item.getContent().stream())
                .filter(Objects::nonNull)
                .filter(content -> "output_text".equals(content.getType()))
                .map(OutputContent::getText)
                .filter(Objects::nonNull)
                .reduce("", String::concat);
        return joined.isBlank() ? null : joined;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputItem {
        private String type;
        private List<OutputContent> content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputContent {
        private String type;
        private String text;
    }
}
