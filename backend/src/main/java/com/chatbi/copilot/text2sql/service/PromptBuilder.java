package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.llm.dto.ChatMessage;
import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds schema-aware chat prompts for Text2SQL. The schema (with comments and business
 * aliases from the semantic layer) is rendered into a compact, LLM-friendly description.
 */
@Component
public class PromptBuilder {

    private final SqlGuardProperties guardProperties;

    public PromptBuilder(SqlGuardProperties guardProperties) {
        this.guardProperties = guardProperties;
    }

    public List<ChatMessage> buildMessages(SchemaInfo schema, String question, List<HistoryTurn> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(schema)));
        if (history != null) {
            for (HistoryTurn turn : history) {
                if (turn == null || !StringUtils.hasText(turn.getQuestion())) {
                    continue;
                }
                messages.add(ChatMessage.user(turn.getQuestion()));
                if (StringUtils.hasText(turn.getSql())) {
                    messages.add(ChatMessage.assistant("{\"sql\": " + toJsonString(turn.getSql())
                            + ", \"needClarification\": false}"));
                }
            }
        }
        messages.add(ChatMessage.user(question));
        return messages;
    }

    public String buildSystemPrompt(SchemaInfo schema) {
        String dialect = dialect(schema == null ? null : schema.getDbType());
        String schemaText = renderSchema(schema);
        return """
                You are a senior data analyst. Translate the user's question into exactly ONE read-only SQL SELECT query for %s.

                Hard rules:
                1. Output exactly ONE SELECT statement. Never write INSERT/UPDATE/DELETE/DDL, and never output multiple statements.
                2. Use ONLY the tables and columns in the schema below. Never invent table or column names.
                3. Use %s dialect syntax and functions. Always include a LIMIT of at most %d rows, unless the result is an aggregate that returns only a few rows.
                4. Add a meaningful ORDER BY for ranking or trend questions.
                5. Map the user's wording to real columns using the business aliases and descriptions.
                6. If the question is ambiguous or cannot be answered from the schema, set needClarification=true and ask ONE short clarifying question instead of guessing.
                7. Write the "explanation" in the SAME language as the user's question.

                Return STRICT JSON only (no markdown, no code fences) with exactly this shape:
                {"sql": "<the SQL, or empty string if clarification needed>", "explanation": "<short explanation>", "needClarification": false, "clarification": ""}

                Database schema:
                %s
                """.formatted(dialect, dialect, guardProperties.getMaxLimit(), schemaText);
    }

    /** Render the schema (tables, columns, comments, semantic aliases) as compact text. */
    public String renderSchema(SchemaInfo schema) {
        if (schema == null || schema.getTables() == null || schema.getTables().isEmpty()) {
            return "(no tables)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-- Database: ").append(schema.getDatabaseName())
                .append(" (").append(dialect(schema.getDbType())).append(")\n");
        for (TableSchema t : schema.getTables()) {
            sb.append("Table ").append(t.getName());
            if (StringUtils.hasText(t.getComment())) {
                sb.append("  (").append(t.getComment().trim()).append(")");
            }
            if (StringUtils.hasText(t.getBusinessAlias())) {
                sb.append("  [alias: ").append(t.getBusinessAlias().trim()).append("]");
            }
            if (StringUtils.hasText(t.getBusinessDescription())) {
                sb.append("  -- ").append(t.getBusinessDescription().trim());
            }
            sb.append("\n");
            for (ColumnSchema c : t.getColumns()) {
                sb.append("  - ").append(c.getName()).append(": ").append(c.getDataType());
                if (c.isPrimaryKey()) {
                    sb.append(" PRIMARY KEY");
                }
                String note = buildColumnNote(c);
                if (!note.isEmpty()) {
                    sb.append("  // ").append(note);
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String buildColumnNote(ColumnSchema c) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(c.getComment())) {
            parts.add(c.getComment().trim());
        }
        if (StringUtils.hasText(c.getBusinessAlias())) {
            parts.add("alias: " + c.getBusinessAlias().trim());
        }
        if (StringUtils.hasText(c.getBusinessDescription())) {
            parts.add(c.getBusinessDescription().trim());
        }
        return String.join("; ", parts);
    }

    private String dialect(String dbType) {
        if (dbType == null) {
            return "SQL";
        }
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "MySQL";
            case "postgresql", "postgres" -> "PostgreSQL";
            default -> dbType;
        };
    }

    private String toJsonString(String raw) {
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
        return "\"" + escaped + "\"";
    }
}
