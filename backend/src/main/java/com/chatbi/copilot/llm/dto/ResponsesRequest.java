package com.chatbi.copilot.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesRequest {

    private String model;
    private List<ChatMessage> input;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    private Map<String, String> reasoning;
    private Boolean stream = false;
}
