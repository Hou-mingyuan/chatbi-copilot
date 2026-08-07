package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.ColumnRef;
import com.chatbi.copilot.permission.DataAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SecureQueryValidator {
    private final SqlGuard sqlGuard;
    private final DataAccessPolicy accessPolicy;
    private final CurrentUserService currentUser;

    public SecureQueryValidator(SqlGuard sqlGuard, DataAccessPolicy accessPolicy,
                                CurrentUserService currentUser) {
        this.sqlGuard = sqlGuard;
        this.accessPolicy = accessPolicy;
        this.currentUser = currentUser;
    }

    public ValidatedQuery validate(String rawSql, Long datasourceId, Capability capability,
                                   SchemaInfo authorizedSchema) {
        SqlInspection inspection = sqlGuard.inspect(rawSql);
        SchemaIndex schema = new SchemaIndex(authorizedSchema);
        if (!schema.tables.containsAll(inspection.tables())) {
            throw new BusinessException("SQL references a table outside the authorized datasource schema");
        }
        Set<ColumnRef> resolvedColumns = new HashSet<>();
        for (RawColumnReference raw : inspection.columns()) {
            if (raw.column().isBlank() || isOutputAlias(raw, inspection, schema)) {
                continue;
            }
            if (!raw.qualifier().isBlank()) {
                String table = inspection.aliases().getOrDefault(raw.qualifier(), normalize(raw.qualifier()));
                if (schema.columns.contains(new ColumnRef(table, raw.column()))) {
                    resolvedColumns.add(new ColumnRef(table, raw.column()));
                }
                continue;
            }
            for (String table : inspection.tables()) {
                ColumnRef candidate = new ColumnRef(table, raw.column());
                if (schema.columns.contains(candidate)) {
                    resolvedColumns.add(candidate);
                }
            }
        }
        AppUserPrincipal user = currentUser.required();
        accessPolicy.authorizeResources(user, datasourceId, capability, inspection.tables(),
                Set.copyOf(resolvedColumns), inspection.projectionWildcards());
        return new ValidatedQuery(inspection.sql(), inspection.tables(), Set.copyOf(resolvedColumns),
                inspection.projectionWildcards(), inspection.functions());
    }

    private boolean isOutputAlias(RawColumnReference raw, SqlInspection inspection, SchemaIndex schema) {
        if (!raw.qualifier().isBlank()) {
            return false;
        }
        return inspection.tables().stream()
                .map(table -> new ColumnRef(table, raw.column()))
                .noneMatch(schema.columns::contains);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private static class SchemaIndex {
        private final Set<String> tables = new HashSet<>();
        private final Set<ColumnRef> columns = new HashSet<>();

        private SchemaIndex(SchemaInfo schema) {
            schema.getTables().forEach(table -> {
                String name = table.getName().toLowerCase(Locale.ROOT);
                tables.add(name);
                table.getColumns().forEach(column -> columns.add(new ColumnRef(name, column.getName())));
            });
        }
    }
}
