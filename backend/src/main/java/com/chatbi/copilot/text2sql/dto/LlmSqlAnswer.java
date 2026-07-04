package com.chatbi.copilot.text2sql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Structured answer we ask the LLM to return as JSON.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmSqlAnswer {

    private String sql;

    /** Short natural-language explanation of the query. */
    private String explanation;

    /** When the question is too ambiguous to answer, the model asks for clarification instead. */
    private boolean needClarification;

    private String clarification;
}
