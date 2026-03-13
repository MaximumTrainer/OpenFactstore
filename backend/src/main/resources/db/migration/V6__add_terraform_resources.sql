-- V6: Environments (with snapshots), Policies, PolicyAttachments, LogicalEnvironments, Organisations

CREATE TABLE environments (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_environments PRIMARY KEY (id),
    CONSTRAINT uq_environments_name UNIQUE (name)
);

CREATE TABLE environment_snapshots (
    id               UUID         NOT NULL,
    environment_id   UUID         NOT NULL,
    snapshot_index   BIGINT       NOT NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL,
    recorded_by      VARCHAR(255) NOT NULL,
    CONSTRAINT pk_environment_snapshots PRIMARY KEY (id),
    CONSTRAINT uq_environment_snapshots_env_idx UNIQUE (environment_id, snapshot_index),
    CONSTRAINT fk_environment_snapshots_env FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE
);

CREATE TABLE snapshot_artifacts (
    id               UUID         NOT NULL,
    snapshot_id      UUID         NOT NULL,
    artifact_sha256  VARCHAR(255) NOT NULL,
    artifact_name    VARCHAR(255) NOT NULL,
    artifact_tag     VARCHAR(255) NOT NULL,
    instance_count   INT          NOT NULL DEFAULT 1,
    CONSTRAINT pk_snapshot_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_snapshot_artifacts_snapshot FOREIGN KEY (snapshot_id) REFERENCES environment_snapshots (id) ON DELETE CASCADE
);

CREATE TABLE policies (
    id                         UUID         NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    enforce_provenance         BOOLEAN      NOT NULL DEFAULT FALSE,
    enforce_trail_compliance   BOOLEAN      NOT NULL DEFAULT FALSE,
    required_attestation_types TEXT         NOT NULL DEFAULT '',
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_policies PRIMARY KEY (id),
    CONSTRAINT uq_policies_name UNIQUE (name)
);

CREATE TABLE policy_attachments (
    id             UUID        NOT NULL,
    policy_id      UUID        NOT NULL,
    environment_id UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_policy_attachments PRIMARY KEY (id),
    CONSTRAINT uq_policy_attachments_policy_env UNIQUE (policy_id, environment_id),
    CONSTRAINT fk_policy_attachments_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_attachments_env FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE
);

CREATE TABLE logical_environments (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_logical_environments PRIMARY KEY (id),
    CONSTRAINT uq_logical_environments_name UNIQUE (name)
);

CREATE TABLE organisations (
    id          UUID         NOT NULL,
    slug        VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_organisations PRIMARY KEY (id),
    CONSTRAINT uq_organisations_slug UNIQUE (slug)
);
