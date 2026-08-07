package com.chatbi.copilot.text2sql.service;

@FunctionalInterface
public interface QueryProgress {
    QueryProgress NONE = (status, progress, message) -> { };

    void stage(String status, int progress, String message);

    default void checkCancelled() {
    }
}
