-- Baseline and additive upgrade for the application metadata store.

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    enabled       INT          DEFAULT 1 NOT NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_session (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    csrf_hash   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    last_seen_at TIMESTAMP,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_session_token UNIQUE (token_hash),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ds_config (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128)  NOT NULL,
    db_type            VARCHAR(32)   NOT NULL,
    host               VARCHAR(255)  NOT NULL,
    port               INT           NOT NULL,
    database_name      VARCHAR(128)  NOT NULL,
    username           VARCHAR(128),
    password           VARCHAR(1024),
    jdbc_params        VARCHAR(512),
    remark             VARCHAR(512),
    created_by         BIGINT,
    verified_read_only INT           DEFAULT 0 NOT NULL,
    last_verified_at   TIMESTAMP,
    deleted            INT           DEFAULT 0,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS verified_read_only INT DEFAULT 0 NOT NULL;
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS datasource_acl (
    user_id             BIGINT NOT NULL,
    datasource_id       BIGINT NOT NULL,
    can_query           INT DEFAULT 0 NOT NULL,
    can_export          INT DEFAULT 0 NOT NULL,
    can_manage_semantic INT DEFAULT 0 NOT NULL,
    granted_by          BIGINT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, datasource_id),
    CONSTRAINT fk_ds_acl_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS table_acl (
    user_id       BIGINT       NOT NULL,
    datasource_id BIGINT       NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    granted_by    BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, datasource_id, table_name),
    CONSTRAINT fk_table_acl_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS column_policy (
    datasource_id BIGINT       NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    column_name   VARCHAR(128) NOT NULL,
    sensitive     INT          DEFAULT 1 NOT NULL,
    label         VARCHAR(255),
    updated_by    BIGINT,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (datasource_id, table_name, column_name)
);

CREATE TABLE IF NOT EXISTS column_acl (
    user_id       BIGINT       NOT NULL,
    datasource_id BIGINT       NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    column_name   VARCHAR(128) NOT NULL,
    granted_by    BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, datasource_id, table_name, column_name),
    CONSTRAINT fk_column_acl_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS query_session (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    datasource_id BIGINT       NOT NULL,
    title         VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_query_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS query_history (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT,
    session_id           VARCHAR(36),
    datasource_id        BIGINT        NOT NULL,
    question             VARCHAR(2000) NOT NULL,
    generated_sql        CLOB,
    edited_sql           CLOB,
    status               VARCHAR(32)   DEFAULT 'SUCCEEDED',
    success              INT           DEFAULT 1,
    error_msg            VARCHAR(1000),
    explanation          VARCHAR(2000),
    clarification        VARCHAR(1000),
    row_count            INT,
    elapsed_ms           BIGINT,
    chart_type           VARCHAR(32),
    risk_level           VARCHAR(32),
    result_snapshot      CLOB,
    result_hash          VARCHAR(64),
    referenced_resources CLOB,
    summary              CLOB,
    request_id           VARCHAR(64),
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE query_history ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS session_id VARCHAR(36);
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS edited_sql CLOB;
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'SUCCEEDED';
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS explanation VARCHAR(2000);
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS clarification VARCHAR(1000);
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32);
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS result_snapshot CLOB;
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS result_hash VARCHAR(64);
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS referenced_resources CLOB;
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS summary CLOB;
ALTER TABLE query_history ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    datasource_id BIGINT        NOT NULL,
    query_id      BIGINT,
    title         VARCHAR(255)  NOT NULL,
    question      VARCHAR(2000) NOT NULL,
    generated_sql CLOB,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE favorite ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE favorite ADD COLUMN IF NOT EXISTS query_id BIGINT;

CREATE TABLE IF NOT EXISTS semantic_model (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id     BIGINT       NOT NULL,
    definition_type   VARCHAR(32)  DEFAULT 'COLUMN' NOT NULL,
    table_name        VARCHAR(128),
    column_name       VARCHAR(128),
    business_alias    VARCHAR(255),
    description       VARCHAR(1000),
    metric_expression VARCHAR(1000),
    aggregation       VARCHAR(32),
    unit              VARCHAR(64),
    time_grain        VARCHAR(32),
    enum_value        VARCHAR(255),
    enum_label        VARCHAR(255),
    related_table     VARCHAR(128),
    related_column    VARCHAR(128),
    join_type         VARCHAR(32),
    version           INT          DEFAULT 1 NOT NULL,
    active            INT          DEFAULT 1 NOT NULL,
    created_by        BIGINT,
    updated_by        BIGINT,
    deleted           INT          DEFAULT 0,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS definition_type VARCHAR(32) DEFAULT 'COLUMN' NOT NULL;
ALTER TABLE semantic_model ALTER COLUMN table_name SET NULL;
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS metric_expression VARCHAR(1000);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS aggregation VARCHAR(32);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS unit VARCHAR(64);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS time_grain VARCHAR(32);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS enum_value VARCHAR(255);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS enum_label VARCHAR(255);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS related_table VARCHAR(128);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS related_column VARCHAR(128);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS join_type VARCHAR(32);
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS version INT DEFAULT 1 NOT NULL;
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS active INT DEFAULT 1 NOT NULL;
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS created_by BIGINT;
ALTER TABLE semantic_model ADD COLUMN IF NOT EXISTS updated_by BIGINT;

CREATE TABLE IF NOT EXISTS semantic_revision (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    semantic_id     BIGINT,
    datasource_id   BIGINT      NOT NULL,
    version         INT         NOT NULL,
    definition_type VARCHAR(32) NOT NULL,
    snapshot        CLOB        NOT NULL,
    changed_by      BIGINT,
    change_type     VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_event (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT,
    request_id    VARCHAR(64)  NOT NULL,
    action        VARCHAR(80)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(128),
    outcome       VARCHAR(32)  NOT NULL,
    details       CLOB,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_session_user_expiry ON auth_session(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_ds_config_created_by ON ds_config(created_by, id);
CREATE INDEX IF NOT EXISTS idx_table_acl_lookup ON table_acl(user_id, datasource_id, table_name);
CREATE INDEX IF NOT EXISTS idx_column_acl_lookup ON column_acl(user_id, datasource_id, table_name, column_name);
CREATE INDEX IF NOT EXISTS idx_query_session_user_ds ON query_session(user_id, datasource_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_query_history_user_ds ON query_history(user_id, datasource_id, id);
CREATE INDEX IF NOT EXISTS idx_query_history_session ON query_history(session_id, id);
CREATE INDEX IF NOT EXISTS idx_favorite_user_ds ON favorite(user_id, datasource_id, id);
CREATE INDEX IF NOT EXISTS idx_semantic_ds_type ON semantic_model(datasource_id, definition_type, active, id);
CREATE INDEX IF NOT EXISTS idx_semantic_revision_entry ON semantic_revision(semantic_id, version);
CREATE INDEX IF NOT EXISTS idx_audit_actor_time ON audit_event(actor_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_request ON audit_event(request_id);
