package com.chatbi.copilot.datasource.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads table / column metadata (including comments and primary keys) from a target database
 * via JDBC {@link DatabaseMetaData}. Results are cached per datasource and can be refreshed.
 */
@Slf4j
@Component
public class SchemaInspector {

    private final DynamicConnectionManager connectionManager;
    private final Map<Long, SchemaInfo> cache = new ConcurrentHashMap<>();

    public SchemaInspector(DynamicConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public SchemaInfo inspect(DataSourceConfig config, boolean refresh) {
        if (!refresh) {
            SchemaInfo cached = cache.get(config.getId());
            if (cached != null) {
                return cached;
            }
        }
        SchemaInfo info = doInspect(config);
        cache.put(config.getId(), info);
        return info;
    }

    public void evict(Long id) {
        cache.remove(id);
    }

    private SchemaInfo doInspect(DataSourceConfig config) {
        DataSource ds = connectionManager.getPool(config);
        SchemaInfo info = new SchemaInfo();
        info.setDatasourceId(config.getId());
        info.setDatabaseName(config.getDatabaseName());
        info.setDbType(config.getDbType());

        boolean isPg = config.getDbType() != null && config.getDbType().toLowerCase().startsWith("postg");

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = isPg ? conn.getCatalog() : config.getDatabaseName();
            String schemaPattern = isPg ? "public" : null;

            try (ResultSet tables = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName == null) {
                        continue;
                    }
                    TableSchema ts = new TableSchema();
                    ts.setName(tableName);
                    ts.setComment(tables.getString("REMARKS"));

                    Set<String> pks = primaryKeys(meta, catalog, schemaPattern, tableName);
                    try (ResultSet cols = meta.getColumns(catalog, schemaPattern, tableName, "%")) {
                        while (cols.next()) {
                            ColumnSchema cs = new ColumnSchema();
                            cs.setName(cols.getString("COLUMN_NAME"));
                            cs.setDataType(cols.getString("TYPE_NAME"));
                            cs.setComment(cols.getString("REMARKS"));
                            cs.setNullable("YES".equalsIgnoreCase(cols.getString("IS_NULLABLE")));
                            cs.setPrimaryKey(pks.contains(cs.getName()));
                            ts.getColumns().add(cs);
                        }
                    }
                    info.getTables().add(ts);
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("Failed to read schema: " + e.getMessage());
        }
        log.info("Inspected schema for datasource id={}: {} tables", config.getId(), info.getTables().size());
        return info;
    }

    private Set<String> primaryKeys(DatabaseMetaData meta, String catalog, String schema, String table) {
        Set<String> pks = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        } catch (SQLException e) {
            log.debug("Could not read primary keys for {}: {}", table, e.getMessage());
        }
        return pks;
    }
}
