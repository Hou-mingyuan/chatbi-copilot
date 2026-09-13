package com.chatbi.copilot.semantic.service;

import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.llm.EmbeddingClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingSchemaIndexTest {

    private static SchemaInfo schema(String... tableNames) {
        SchemaInfo schema = new SchemaInfo();
        schema.setDatasourceId(1L);
        for (String name : tableNames) {
            TableSchema table = new TableSchema();
            table.setName(name);
            ColumnSchema column = new ColumnSchema();
            column.setName(name + "_id");
            table.getColumns().add(column);
            schema.getTables().add(table);
        }
        return schema;
    }

    @Test
    void returnsEmptyWhenClientDisabled() {
        EmbeddingClient disabled = stub(false, inputs -> List.of());
        EmbeddingSchemaIndex index = new EmbeddingSchemaIndex(disabled);

        assertTrue(index.tableSimilarities(schema("orders"), "????").isEmpty());
    }

    @Test
    void ranksTablesByCosineSimilarityToTheQuestion() {
        // the question vector points at the orders direction
        EmbeddingClient client = stub(true, inputs -> {
            List<double[]> vectors = new ArrayList<>();
            for (String text : inputs) {
                boolean ordersDirection = text.startsWith("????") || text.startsWith("orders");
                vectors.add(ordersDirection ? new double[] {1, 0} : new double[] {0, 1});
            }
            return vectors;
        });
        EmbeddingSchemaIndex index = new EmbeddingSchemaIndex(client);

        Map<String, Double> similarities = index.tableSimilarities(schema("orders", "regions"), "????");

        assertEquals(2, similarities.size());
        assertTrue(similarities.get("orders") > similarities.get("regions"));
    }

    @Test
    void cachesVectorsPerSchemaFingerprint() {
        AtomicInteger embedCalls = new AtomicInteger();
        EmbeddingClient counting = stub(true, inputs -> {
            embedCalls.incrementAndGet();
            return inputs.stream().map(text -> new double[] {1, 0}).toList();
        });
        EmbeddingSchemaIndex index = new EmbeddingSchemaIndex(counting);
        SchemaInfo schema = schema("orders", "regions");

        index.tableSimilarities(schema, "?????");
        index.tableSimilarities(schema, "?????");

        // first query embeds the schema documents once; each query embeds only the question
        assertEquals(3, embedCalls.get());
    }

    @Test
    void fallsBackToEmptyOnClientFailure() {
        EmbeddingClient failing = stub(true, inputs -> { throw new IllegalStateException("embeddings down"); });
        EmbeddingSchemaIndex index = new EmbeddingSchemaIndex(failing);

        assertTrue(index.tableSimilarities(schema("orders"), "????").isEmpty());
    }

    private interface StubEmbed {
        List<double[]> embed(List<String> inputs);
    }

    private EmbeddingClient stub(boolean enabled, StubEmbed embed) {
        return new EmbeddingClient(new com.chatbi.copilot.config.LlmProperties()) {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public List<double[]> embed(List<String> inputs) {
                return embed.embed(inputs);
            }
        };
    }
}
