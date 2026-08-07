package com.chatbi.copilot.datasource.service.readonly;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class MySqlReadOnlyAccountVerifier implements ReadOnlyAccountVerifier {
    private static final Pattern PRIVILEGE_GRANT = Pattern.compile("^GRANT\\s+(.+?)\\s+ON\\s+.+$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_PRIVILEGES = Set.of("USAGE", "SELECT", "SHOW VIEW");

    @Override
    public boolean supports(String dbType) {
        return "mysql".equalsIgnoreCase(dbType);
    }

    @Override
    public ReadOnlyVerification verify(Connection connection) throws java.sql.SQLException {
        List<String> evidence = new ArrayList<>();
        boolean safe = true;
        try (Statement statement = connection.createStatement();
             ResultSet grants = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (grants.next()) {
                String grant = grants.getString(1).toUpperCase(Locale.ROOT);
                safe &= isSelectOnlyGrant(grant);
                evidence.add(redactGrant(grant));
            }
        }
        return new ReadOnlyVerification(safe, List.copyOf(evidence));
    }

    private boolean isSelectOnlyGrant(String grant) {
        java.util.regex.Matcher matcher = PRIVILEGE_GRANT.matcher(grant);
        if (!matcher.matches() || grant.contains(" GRANT OPTION")) {
            return false;
        }
        for (String privilege : matcher.group(1).split(",")) {
            if (!ALLOWED_PRIVILEGES.contains(privilege.trim())) {
                return false;
            }
        }
        return true;
    }

    private String redactGrant(String grant) {
        int identified = grant.indexOf(" IDENTIFIED BY ");
        return identified >= 0 ? grant.substring(0, identified) + " [REDACTED]" : grant;
    }
}
