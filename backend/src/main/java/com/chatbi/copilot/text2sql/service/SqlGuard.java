package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlGuard {
    private static final Pattern FORBIDDEN_TOKENS = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|merge|call|exec|execute|use|attach|detach|shutdown|rename|vacuum|reindex|lock|kill|copy)\\b");
    private static final Pattern FILE_EXPORT = Pattern.compile(
            "(?i)\\binto\\s+(out|dump)file\\b");
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
            "sleep", "pg_sleep", "benchmark", "load_file", "pg_read_file", "pg_read_binary_file",
            "lo_import", "lo_export", "nextval", "setval", "set_config", "dblink", "dblink_exec",
            "pg_advisory_lock", "pg_advisory_xact_lock", "get_lock", "release_lock", "sys_eval", "sys_exec");

    private final SqlGuardProperties properties;

    public SqlGuard(SqlGuardProperties properties) {
        this.properties = properties;
    }

    public String sanitize(String rawSql) {
        return inspect(rawSql).sql();
    }

    public SqlInspection inspect(String rawSql) {
        String sql = clean(rawSql);
        if (sql.isBlank()) {
            throw new BusinessException("SQL is empty");
        }
        Statements parsed;
        try {
            parsed = CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            throw new BusinessException("SQL could not be parsed and was rejected");
        }
        List<Statement> statements = parsed.getStatements();
        if (statements == null || statements.size() != 1 || !(statements.get(0) instanceof Select select)) {
            throw new BusinessException("Exactly one read-only SELECT statement is allowed");
        }

        String scannable = stripQuotedText(stripComments(sql));
        if (FORBIDDEN_TOKENS.matcher(scannable).find()) {
            throw new BusinessException("SQL contains a write, administration, or locking operation");
        }
        if (FILE_EXPORT.matcher(scannable).find()) {
            throw new BusinessException("Database file export is not allowed");
        }

        validateSelectTree(select);
        ResourceCollector collector = new ResourceCollector();
        Set<String> tableNames = collector.getTables((Statement) select);
        Set<String> tables = tableNames.stream().map(SqlGuard::normalizeTable)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (collector.nextValueSeen || collector.functions.stream().anyMatch(DANGEROUS_FUNCTIONS::contains)) {
            throw new BusinessException("SQL uses a function with file, timing, locking, or mutation side effects");
        }

        Set<String> wildcardQualifiers = new LinkedHashSet<>();
        collectProjectionWildcards(select, wildcardQualifiers);
        Set<String> resolvedWildcards = new LinkedHashSet<>();
        for (String qualifier : wildcardQualifiers) {
            if (qualifier.isBlank()) {
                resolvedWildcards.addAll(tables);
            } else {
                resolvedWildcards.add(collector.aliases.getOrDefault(qualifier, normalizeTable(qualifier)));
            }
        }

        enforceLimit(select);
        return new SqlInspection(select.toString(), Set.copyOf(tables), Set.copyOf(collector.columns),
                Map.copyOf(collector.aliases), Set.copyOf(resolvedWildcards), Set.copyOf(collector.functions));
    }

    private void validateSelectTree(Select select) {
        if (select.getForClause() != null || select.getFetch() != null || select.getLimitBy() != null) {
            throw new BusinessException("Locking, FETCH, and LIMIT BY query variants are not allowed");
        }
        validateOffset(select);
        if (select.getWithItemsList() != null) {
            for (WithItem item : select.getWithItemsList()) {
                if (item == null || item.getSelect() == null) {
                    throw new BusinessException("Writable or invalid CTE is not allowed");
                }
                validateSelectTree(item.getSelect());
            }
        }
        if (select instanceof PlainSelect plain) {
            if ((plain.getIntoTables() != null && !plain.getIntoTables().isEmpty())
                    || plain.getIntoTempTable() != null) {
                throw new BusinessException("SELECT INTO and temporary table creation are not allowed");
            }
            if (plain.getForMode() != null || plain.getForUpdateTable() != null
                    || plain.isNoWait() || plain.isSkipLocked()) {
                throw new BusinessException("Row-locking SELECT variants are not allowed");
            }
            if (plain.getTop() != null || plain.getFirst() != null || plain.getSkip() != null) {
                throw new BusinessException("TOP/FIRST/SKIP variants are not allowed; use a literal LIMIT");
            }
        } else if (select instanceof SetOperationList setOperation) {
            for (Select child : setOperation.getSelects()) {
                validateSelectTree(child);
            }
        } else if (select instanceof ParenthesedSelect parenthesed) {
            if (parenthesed.getSelect() == null) {
                throw new BusinessException("Invalid parenthesized SELECT");
            }
            validateSelectTree(parenthesed.getSelect());
        }
    }

    private void validateOffset(Select select) {
        if (select.getOffset() != null) {
            if (!(select.getOffset().getOffset() instanceof LongValue value)
                    || value.getValue() < 0 || value.getValue() > properties.getMaxOffset()) {
                throw new BusinessException("OFFSET must be a literal value within the configured maximum");
            }
        }
        Limit limit = select.getLimit();
        if (limit != null && limit.getOffset() != null) {
            if (!(limit.getOffset() instanceof LongValue value)
                    || value.getValue() < 0 || value.getValue() > properties.getMaxOffset()) {
                throw new BusinessException("LIMIT offset must be a literal value within the configured maximum");
            }
        }
    }

    private void enforceLimit(Select select) {
        Limit limit = select.getLimit();
        if (limit == null) {
            limit = new Limit();
            limit.setRowCount(new LongValue(properties.getDefaultLimit()));
            select.setLimit(limit);
            return;
        }
        if (limit.isLimitAll() || limit.isLimitNull() || !(limit.getRowCount() instanceof LongValue rowCount)) {
            throw new BusinessException("LIMIT must be a non-negative literal number");
        }
        if (rowCount.getValue() < 0) {
            throw new BusinessException("LIMIT must be a non-negative literal number");
        }
        if (rowCount.getValue() > properties.getMaxLimit()) {
            limit.setRowCount(new LongValue(properties.getMaxLimit()));
        }
    }

    private void collectProjectionWildcards(Select select, Set<String> target) {
        if (select.getWithItemsList() != null) {
            select.getWithItemsList().forEach(item -> collectProjectionWildcards(item.getSelect(), target));
        }
        if (select instanceof PlainSelect plain) {
            for (SelectItem<?> item : plain.getSelectItems()) {
                if (item.getExpression() instanceof AllTableColumns tableColumns) {
                    target.add(normalizeIdentifier(tableColumns.getTable().getName()));
                } else if (item.getExpression() instanceof AllColumns) {
                    target.add("");
                } else if (item.getExpression() instanceof ParenthesedSelect nested) {
                    collectProjectionWildcards(nested.getSelect(), target);
                }
            }
        } else if (select instanceof SetOperationList setOperation) {
            setOperation.getSelects().forEach(child -> collectProjectionWildcards(child, target));
        } else if (select instanceof ParenthesedSelect parenthesed) {
            collectProjectionWildcards(parenthesed.getSelect(), target);
        }
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim()
                .replaceFirst("(?is)^```(?:sql)?\\s*", "")
                .replaceFirst("(?is)\\s*```$", "")
                .trim();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ")
                .replaceAll("#[^\\n]*", " ");
    }

    private String stripQuotedText(String sql) {
        return sql.replaceAll("'(?:''|[^'])*'", "''")
                .replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"")
                .replaceAll("`[^`]*`", "``");
    }

    private static String normalizeTable(String name) {
        String normalized = normalizeIdentifier(name);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private static String normalizeIdentifier(String name) {
        return name == null ? "" : name.replace("`", "").replace("\"", "")
                .trim().toLowerCase(Locale.ROOT);
    }

    private static class ResourceCollector extends TablesNamesFinder {
        private final Set<RawColumnReference> columns = new LinkedHashSet<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private final Set<String> functions = new LinkedHashSet<>();
        private boolean nextValueSeen;

        @Override
        public void visit(Table table) {
            String physical = normalizeTable(table.getFullyQualifiedName());
            if (table.getAlias() != null) {
                aliases.put(normalizeIdentifier(table.getAlias().getName()), physical);
            }
            aliases.putIfAbsent(normalizeIdentifier(table.getName()), physical);
            super.visit(table);
        }

        @Override
        public void visit(Column column) {
            String qualifier = column.getTable() == null ? "" : column.getTable().getName();
            columns.add(new RawColumnReference(qualifier, column.getColumnName()));
            super.visit(column);
        }

        @Override
        public void visit(Function function) {
            functions.add(normalizeIdentifier(function.getName()));
            super.visit(function);
        }

        @Override
        public void visit(NextValExpression nextValExpression) {
            nextValueSeen = true;
            super.visit(nextValExpression);
        }
    }
}
