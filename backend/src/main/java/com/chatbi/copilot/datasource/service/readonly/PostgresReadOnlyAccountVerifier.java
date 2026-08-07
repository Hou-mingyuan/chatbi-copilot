package com.chatbi.copilot.datasource.service.readonly;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class PostgresReadOnlyAccountVerifier implements ReadOnlyAccountVerifier {
    @Override
    public boolean supports(String dbType) {
        return dbType != null && dbType.toLowerCase().startsWith("postg");
    }

    @Override
    public ReadOnlyVerification verify(Connection connection) throws java.sql.SQLException {
        List<String> evidence = new ArrayList<>();
        boolean elevated;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls "
                        + "FROM pg_roles WHERE rolname = current_user");
             ResultSet rs = statement.executeQuery()) {
            elevated = !rs.next() || rs.getBoolean(1);
        }
        evidence.add(elevated ? "role has elevated cluster capability" : "role has no elevated cluster capability");

        boolean databaseCreate;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT has_database_privilege(current_user, current_database(), 'CREATE')");
             ResultSet rs = statement.executeQuery()) {
            databaseCreate = rs.next() && rs.getBoolean(1);
        }
        evidence.add(databaseCreate ? "role can CREATE in database" : "role cannot CREATE in database");

        int writeGrants;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE c.relkind IN ('r','p','v','m','f') "
                        + "AND n.nspname NOT IN ('pg_catalog','information_schema') "
                        + "AND has_table_privilege(current_user, c.oid, "
                        + "'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            writeGrants = rs.getInt(1);
        }
        evidence.add("write-capable tables=" + writeGrants);

        int writableSchemas;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pg_namespace n "
                        + "WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname <> 'information_schema' "
                        + "AND has_schema_privilege(current_user, n.oid, 'CREATE')");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            writableSchemas = rs.getInt(1);
        }
        evidence.add("writable schemas=" + writableSchemas);
        return new ReadOnlyVerification(!elevated && !databaseCreate && writeGrants == 0 && writableSchemas == 0,
                List.copyOf(evidence));
    }
}
