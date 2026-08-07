CREATE TABLE IF NOT EXISTS query_job (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    datasource_id   BIGINT       NOT NULL,
    session_id      VARCHAR(36),
    job_type        VARCHAR(16)   NOT NULL,
    request_json    CLOB          NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    progress        INT           DEFAULT 0 NOT NULL,
    message         VARCHAR(255),
    result_query_id BIGINT,
    error_message   VARCHAR(1000),
    cancel_requested INT          DEFAULT 0 NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_query_job_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_query_job_user_time ON query_job(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_query_job_status ON query_job(status, updated_at);
