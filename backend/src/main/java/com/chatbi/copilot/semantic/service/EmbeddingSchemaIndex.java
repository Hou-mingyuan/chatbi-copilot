package com.chatbi.copilot.semantic.service;

import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.llm.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Embedding-backed table recall over the authorized schema.
 *
 * <p>Per datasource, each table is described once (name + alias + comment + business description
 * + column names) and embedded; vectors are cached in memory keyed by the schema fingerprint so
 * repeated queries only embed the question. On any embedding failure the caller must fall back to
 * lexical scoring.
 */
@Service
public class EmbeddingSchemaIndex {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSchemaIndex.class);

    private final EmbeddingClient embeddingClient;
    private final Map<Long, CachedIndex> cache = new HashMap<>();

    public EmbeddingSchemaIndex(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    /**
     * Returns cosine similarities for every table against the question, or an empty map when
     * embeddings are disabled or unavailable.
     */
    public Map<String, Double> tableSimilarities(SchemaInfo schema, String question) {
        if (!embeddingClient.isEnabled() || question == null || question.isBlank()) {
            return Map.of();
        }
        try {
            CachedIndex index = indexFor(schema);
            if (index.vectors().isEmpty()) {
                return Map.of();
            }
            List<double[]> questionVector = embeddingClient.embed(List.of(question));
            if (questionVector.size() != 1) {
                return Map.of();
            }
            Map<String, Double> similarities = new HashMap<>();
            for (int i = 0; i < index.tables().size(); i++) {
                double similarity = EmbeddingClient.cosine(questionVector.get(0), index.vectors().get(i));
                similarities.put(index.tables().get(i), similarity);
            }
            return similarities;
        } catch (Exception e) {
            log.warn("Embedding schema recall failed, falling back to lexical: {}", e.getMessage());
            return Map.of();
        }
    }

    private synchronized CachedIndex indexFor(SchemaInfo schema) {
        long datasourceId = schema.getDatasourceId();
        String fingerprint = fingerprint(schema);
        CachedIndex cached = cache.get(datasourceId);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached;
        }
        List<TableSchema> tables = schema.getTables();
        if (tables.isEmpty()) {
            return new CachedIndex(fingerprint, List.of(), List.of());
        }
        List<String> documents = new ArrayList<>();
        for (TableSchema table : tables) {
            documents.add(tableDocument(table));
        }
        List<double[]> vectors = embeddingClient.embed(documents);
        if (vectors.size() != documents.size()) {
            log.warn("Embedding count mismatch for datasource {}: expected {}, got {}",
                    datasourceId, documents.size(), vectors.size());
            return new CachedIndex(fingerprint, List.of(), List.of());
        }
        List<String> names = tables.stream().map(table -> table.getName().trim().toLowerCase()).toList();
        CachedIndex index = new CachedIndex(fingerprint, names, vectors);
        cache.put(datasourceId, index);
        return index;
    }

    private String fingerprint(SchemaInfo schema) {
        StringBuilder fingerprint = new StringBuilder();
        for (TableSchema table : schema.getTables()) {
            fingerprint.append(table.getName()).append(':');
            for (ColumnSchema column : table.getColumns()) {
                fingerprint.append(column.getName()).append(',');
            }
            fingerprint.append(';');
        }
        return Integer.toString(fingerprint.toString().hashCode());
    }

    private String tableDocument(TableSchema table) {
        StringBuilder document = new StringBuilder();
        document.append(nullToEmpty(table.getName()));
        String alias = nullToEmpty(table.getBusinessAlias());
        if (!alias.isBlank() && !document.toString().contains(alias)) {
            document.append(' ').append(alias);
        }
        String comment = nullToEmpty(table.getComment());
        if (!comment.isBlank()) {
            document.append(' ').append(comment);
        }
        String description = nullToEmpty(table.getBusinessDescription());
        if (!description.isBlank()) {
            document.append(' ').append(description);
        }
        List<String> columns = table.getColumns().stream()
                .map(ColumnSchema::getName)
                .filter(Objects::nonNull)
                .limit(40)
                .toList();
        if (!columns.isEmpty()) {
            document.append(" columns: ").append(String.join(", ", columns));
        }
        return document.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedIndex(String fingerprint, List<String> tables, List<double[]> vectors) {}
}
