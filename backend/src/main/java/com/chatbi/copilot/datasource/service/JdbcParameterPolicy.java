package com.chatbi.copilot.datasource.service;

import com.chatbi.copilot.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JdbcParameterPolicy {
    private static final Set<String> MYSQL_ALLOWED = Set.of(
            "sslmode", "servertimezone", "connecttimeout", "sockettimeout",
            "useunicode", "characterencoding", "applicationname");
    private static final Set<String> POSTGRES_ALLOWED = Set.of(
            "sslmode", "connecttimeout", "sockettimeout", "applicationname", "currentschema");
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,39}");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9._:/+-]{0,160}");

    public String sanitize(String dbType, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Set<String> allowed = dbType != null && dbType.toLowerCase(Locale.ROOT).startsWith("postg")
                ? POSTGRES_ALLOWED : MYSQL_ALLOWED;
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : raw.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2 || !KEY.matcher(parts[0]).matches() || !VALUE.matcher(parts[1]).matches()) {
                throw new BusinessException("Invalid JDBC parameter format");
            }
            String normalizedKey = parts[0].toLowerCase(Locale.ROOT);
            if (!allowed.contains(normalizedKey)) {
                throw new BusinessException("JDBC parameter is not allowed: " + parts[0]);
            }
            if (params.putIfAbsent(parts[0], parts[1]) != null) {
                throw new BusinessException("Duplicate JDBC parameter: " + parts[0]);
            }
        }
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
    }
}
