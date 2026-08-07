package com.chatbi.copilot.semantic.service;

import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.semantic.SemanticDefinitionType;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SemanticContextRetriever {
    private static final int MAX_TABLES = 8;
    private static final int MAX_COLUMNS = 80;
    private static final Pattern TERM_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final SemanticService semanticService;

    public SemanticContextRetriever(SemanticService semanticService) {
        this.semanticService = semanticService;
    }

    public SemanticContext retrieve(SchemaInfo authorizedSchema, String question) {
        String normalizedQuestion = normalize(question);
        List<SemanticModel> definitions = semanticService.listActiveUnchecked(authorizedSchema.getDatasourceId());
        Map<String, Integer> scores = scoreTables(authorizedSchema, definitions, normalizedQuestion);
        LinkedHashSet<String> selected = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TABLES)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty()) {
            authorizedSchema.getTables().stream().limit(Math.min(6, MAX_TABLES))
                    .map(table -> normalize(table.getName())).forEach(selected::add);
        }
        addJoinNeighbors(selected, definitions);
        while (selected.size() > MAX_TABLES) {
            String last = selected.stream().reduce((first, second) -> second).orElseThrow();
            selected.remove(last);
        }

        SchemaInfo compact = new SchemaInfo();
        compact.setDatasourceId(authorizedSchema.getDatasourceId());
        compact.setDatabaseName(authorizedSchema.getDatabaseName());
        compact.setDbType(authorizedSchema.getDbType());
        int totalColumns = authorizedSchema.getTables().stream().mapToInt(table -> table.getColumns().size()).sum();
        int selectedColumnCount = 0;
        for (TableSchema source : authorizedSchema.getTables()) {
            if (!selected.contains(normalize(source.getName())) || selectedColumnCount >= MAX_COLUMNS) {
                continue;
            }
            TableSchema target = copyTableWithoutColumns(source);
            for (ColumnSchema column : source.getColumns()) {
                if (selectedColumnCount >= MAX_COLUMNS) {
                    break;
                }
                target.getColumns().add(copyColumn(column));
                selectedColumnCount++;
            }
            compact.getTables().add(target);
        }
        List<SemanticModel> selectedDefinitions = definitions.stream()
                .filter(model -> selected.contains(normalize(model.getTableName()))
                        || selected.contains(normalize(model.getRelatedTable())))
                .filter(model -> isCoreDefinition(model) || matches(normalizedQuestion, searchableText(model)))
                .limit(60)
                .toList();
        boolean truncated = compact.getTables().size() < authorizedSchema.getTables().size()
                || selectedColumnCount < totalColumns || selectedDefinitions.size() < definitions.size();
        return new SemanticContext(compact, selectedDefinitions, authorizedSchema.getTables().size(),
                compact.getTables().size(), totalColumns, selectedColumnCount, truncated);
    }

    private Map<String, Integer> scoreTables(SchemaInfo schema, List<SemanticModel> definitions, String question) {
        Map<String, Integer> scores = new HashMap<>();
        for (TableSchema table : schema.getTables()) {
            String name = normalize(table.getName());
            int score = matches(question, name) ? 12 : 0;
            if (matches(question, table.getBusinessAlias())) score += 10;
            if (matches(question, table.getComment())) score += 3;
            for (ColumnSchema column : table.getColumns()) {
                if (matches(question, column.getName())) score += 4;
                if (matches(question, column.getBusinessAlias())) score += 8;
                if (matches(question, column.getComment())) score += 2;
            }
            scores.put(name, score);
        }
        for (SemanticModel model : definitions) {
            String table = normalize(model.getTableName());
            if (scores.containsKey(table) && matches(question, searchableText(model))) {
                scores.merge(table, 9, Integer::sum);
            }
        }
        return scores;
    }

    private void addJoinNeighbors(Set<String> selected, List<SemanticModel> definitions) {
        List<String> additions = new ArrayList<>();
        for (SemanticModel model : definitions) {
            if (!SemanticDefinitionType.JOIN.name().equalsIgnoreCase(model.getDefinitionType())) {
                continue;
            }
            String left = normalize(model.getTableName());
            String right = normalize(model.getRelatedTable());
            if (selected.contains(left)) additions.add(right);
            if (selected.contains(right)) additions.add(left);
        }
        additions.stream().filter(value -> value != null && !value.isBlank()).forEach(selected::add);
    }

    private boolean isCoreDefinition(SemanticModel model) {
        return switch (SemanticDefinitionType.valueOf(model.getDefinitionType().toUpperCase(Locale.ROOT))) {
            case JOIN, METRIC, TIME -> true;
            default -> false;
        };
    }

    private String searchableText(SemanticModel model) {
        return String.join(" ", nullToEmpty(model.getTableName()), nullToEmpty(model.getColumnName()),
                nullToEmpty(model.getBusinessAlias()), nullToEmpty(model.getDescription()),
                nullToEmpty(model.getEnumValue()), nullToEmpty(model.getEnumLabel()),
                nullToEmpty(model.getRelatedTable()), nullToEmpty(model.getUnit()));
    }

    private boolean matches(String question, String candidate) {
        String normalized = normalize(candidate);
        if (question.isBlank() || normalized.isBlank()) {
            return false;
        }
        if (question.contains(normalized) || normalized.contains(question)) {
            return true;
        }
        for (String term : TERM_SPLIT.split(normalized)) {
            if (term.length() >= 2 && question.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private TableSchema copyTableWithoutColumns(TableSchema source) {
        TableSchema target = new TableSchema();
        target.setName(source.getName());
        target.setComment(source.getComment());
        target.setBusinessAlias(source.getBusinessAlias());
        target.setBusinessDescription(source.getBusinessDescription());
        return target;
    }

    private ColumnSchema copyColumn(ColumnSchema source) {
        ColumnSchema target = new ColumnSchema();
        target.setName(source.getName());
        target.setDataType(source.getDataType());
        target.setComment(source.getComment());
        target.setNullable(source.isNullable());
        target.setPrimaryKey(source.isPrimaryKey());
        target.setBusinessAlias(source.getBusinessAlias());
        target.setBusinessDescription(source.getBusinessDescription());
        return target;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
