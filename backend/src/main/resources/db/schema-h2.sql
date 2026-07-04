-- Application metadata schema (H2, MySQL compatibility mode).
-- Stores connection configs, query history, favorites and the semantic layer.
-- Target business databases are NOT touched by these tables.

CREATE TABLE IF NOT EXISTS ds_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL,
    db_type       VARCHAR(32)   NOT NULL,        -- mysql | postgresql
    host          VARCHAR(255)  NOT NULL,
    port          INT           NOT NULL,
    database_name VARCHAR(128)  NOT NULL,
    username      VARCHAR(128),
    password      VARCHAR(1024),                 -- AES-GCM encrypted (enc: prefix)
    jdbc_params   VARCHAR(512),
    remark        VARCHAR(512),
    deleted       INT           DEFAULT 0,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS query_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT        NOT NULL,
    question      VARCHAR(2000) NOT NULL,
    generated_sql CLOB,
    success       INT           DEFAULT 1,
    error_msg     VARCHAR(2000),
    row_count     INT,
    elapsed_ms    BIGINT,
    chart_type    VARCHAR(32),
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT        NOT NULL,
    title         VARCHAR(255)  NOT NULL,
    question      VARCHAR(2000) NOT NULL,
    generated_sql CLOB,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS semantic_model (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id  BIGINT       NOT NULL,
    table_name     VARCHAR(128) NOT NULL,
    column_name    VARCHAR(128),                 -- NULL => table-level entry
    business_alias VARCHAR(255),
    description    VARCHAR(1000),
    deleted        INT          DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
