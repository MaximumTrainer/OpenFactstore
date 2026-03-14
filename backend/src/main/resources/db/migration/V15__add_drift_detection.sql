ALTER TABLE environments ADD COLUMN drift_policy VARCHAR(50) NOT NULL DEFAULT 'WARN';

CREATE TABLE environment_baselines (
    id              UUID         NOT NULL,
    environment_id  UUID         NOT NULL,
    snapshot_id     UUID,
    approved_by     VARCHAR(255) NOT NULL,
    approved_at     TIMESTAMPTZ  NOT NULL,
    description     TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_environment_baselines PRIMARY KEY (id),
    CONSTRAINT fk_env_baselines_env FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE
);

CREATE TABLE drift_reports (
    id                  UUID        NOT NULL,
    environment_id      UUID        NOT NULL,
    baseline_id         UUID,
    snapshot_id         UUID        NOT NULL,
    generated_at        TIMESTAMPTZ NOT NULL,
    has_drift           BOOLEAN     NOT NULL,
    added_artifacts     TEXT,
    removed_artifacts   TEXT,
    updated_artifacts   TEXT,
    CONSTRAINT pk_drift_reports PRIMARY KEY (id),
    CONSTRAINT fk_drift_reports_env FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE
);
