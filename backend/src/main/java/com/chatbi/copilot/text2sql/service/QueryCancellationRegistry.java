package com.chatbi.copilot.text2sql.service;

import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QueryCancellationRegistry {
    private final Map<String, Statement> statements = new ConcurrentHashMap<>();

    public void register(String executionId, Statement statement) {
        if (executionId != null && statement != null) {
            statements.put(executionId, statement);
        }
    }

    public void unregister(String executionId, Statement statement) {
        if (executionId != null) {
            statements.remove(executionId, statement);
        }
    }

    public boolean cancel(String executionId) {
        Statement statement = statements.remove(executionId);
        if (statement == null) {
            return false;
        }
        try {
            statement.cancel();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
