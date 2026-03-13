-- V1__initial_schema.sql
-- Initial schema migration for Factstore.
-- Creates all tables derived from the JPA entity definitions.

CREATE TABLE flows (
    id                         UUID         NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    description                TEXT         NOT NULL DEFAULT '',
    required_attestation_types TEXT,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_flows PRIMARY KEY (id),
    CONSTRAINT uq_flows_name UNIQUE (name)
);

CREATE TABLE trails (
    id                    UUID         NOT NULL,
    flow_id               UUID         NOT NULL,
    git_commit_sha        VARCHAR(255) NOT NULL,
    git_branch            VARCHAR(255) NOT NULL,
    git_author            VARCHAR(255) NOT NULL,
    git_author_email      VARCHAR(255) NOT NULL,
    pull_request_id       VARCHAR(255),
    pull_request_reviewer VARCHAR(255),
    deployment_actor      VARCHAR(255),
    status                VARCHAR(50)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_trails PRIMARY KEY (id),
    CONSTRAINT fk_trails_flow FOREIGN KEY (flow_id) REFERENCES flows (id) ON DELETE CASCADE
);

CREATE TABLE artifacts (
    id            UUID         NOT NULL,
    trail_id      UUID         NOT NULL,
    image_name    VARCHAR(255) NOT NULL,
    image_tag     VARCHAR(255) NOT NULL,
    sha256_digest VARCHAR(255) NOT NULL,
    registry      VARCHAR(255),
    reported_at   TIMESTAMPTZ  NOT NULL,
    reported_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_artifacts_trail FOREIGN KEY (trail_id) REFERENCES trails (id)
);

CREATE TABLE attestations (
    id                       UUID         NOT NULL,
    trail_id                 UUID         NOT NULL,
    type                     VARCHAR(255) NOT NULL,
    status                   VARCHAR(50)  NOT NULL,
    evidence_file_hash       VARCHAR(255),
    evidence_file_name       VARCHAR(255),
    evidence_file_size_bytes BIGINT,
    details                  TEXT,
    created_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_attestations PRIMARY KEY (id),
    CONSTRAINT fk_attestations_trail FOREIGN KEY (trail_id) REFERENCES trails (id)
);

CREATE TABLE evidence_files (
    id              UUID         NOT NULL,
    attestation_id  UUID         NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    sha256_hash     VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    content_type    VARCHAR(255) NOT NULL,
    stored_at       TIMESTAMPTZ  NOT NULL,
    content         BYTEA        NOT NULL,
    CONSTRAINT pk_evidence_files PRIMARY KEY (id),
    CONSTRAINT fk_evidence_files_attestation FOREIGN KEY (attestation_id) REFERENCES attestations (id)
);

CREATE TABLE webhook_configs (
    id         UUID         NOT NULL,
    source     VARCHAR(50)  NOT NULL,
    secret     VARCHAR(255) NOT NULL,
    flow_id    UUID         NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_webhook_configs PRIMARY KEY (id),
    CONSTRAINT fk_webhook_configs_flow FOREIGN KEY (flow_id) REFERENCES flows (id)
);

CREATE TABLE webhook_deliveries (
    id                UUID         NOT NULL,
    webhook_config_id UUID         NOT NULL,
    delivery_id       VARCHAR(255) NOT NULL,
    source            VARCHAR(50)  NOT NULL,
    event_type        VARCHAR(255),
    status            VARCHAR(50)  NOT NULL,
    status_message    TEXT,
    received_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_webhook_deliveries_config FOREIGN KEY (webhook_config_id) REFERENCES webhook_configs (id) ON DELETE CASCADE
);

CREATE TABLE jira_configs (
    id                  UUID         NOT NULL,
    jira_base_url       VARCHAR(255) NOT NULL,
    jira_username       VARCHAR(255) NOT NULL,
    jira_api_token      VARCHAR(255) NOT NULL,
    default_project_key VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_jira_configs PRIMARY KEY (id)
);

CREATE TABLE jira_tickets (
    id          UUID         NOT NULL,
    ticket_key  VARCHAR(255) NOT NULL,
    summary     VARCHAR(512) NOT NULL,
    status      VARCHAR(255) NOT NULL,
    issue_type  VARCHAR(255) NOT NULL,
    trail_id    UUID,
    project_key VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_jira_tickets PRIMARY KEY (id),
    CONSTRAINT fk_jira_tickets_trail FOREIGN KEY (trail_id) REFERENCES trails (id)
);

CREATE TABLE slack_configs (
    id             UUID         NOT NULL,
    org_slug       VARCHAR(255) NOT NULL,
    bot_token      VARCHAR(255) NOT NULL,
    signing_secret VARCHAR(255) NOT NULL,
    channel        VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_slack_configs PRIMARY KEY (id),
    CONSTRAINT uq_slack_configs_org_slug UNIQUE (org_slug)
);

CREATE TABLE confluence_configs (
    id                   UUID         NOT NULL,
    confluence_base_url  VARCHAR(255) NOT NULL,
    confluence_username  VARCHAR(255) NOT NULL,
    confluence_api_token VARCHAR(255) NOT NULL,
    default_space_key    VARCHAR(255) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_confluence_configs PRIMARY KEY (id)
);
