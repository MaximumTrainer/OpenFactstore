-- V10: Logical environment members (logical_environments table already created in V6)

CREATE TABLE logical_environment_members (
    id               UUID        NOT NULL,
    logical_env_id   UUID        NOT NULL,
    physical_env_id  UUID        NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_logical_environment_members PRIMARY KEY (id),
    CONSTRAINT uq_logical_env_member UNIQUE (logical_env_id, physical_env_id),
    CONSTRAINT fk_logical_env_members_logical FOREIGN KEY (logical_env_id) REFERENCES logical_environments (id) ON DELETE CASCADE,
    CONSTRAINT fk_logical_env_members_physical FOREIGN KEY (physical_env_id) REFERENCES environments (id) ON DELETE CASCADE
);

CREATE INDEX idx_logical_env_members_logical_id ON logical_environment_members (logical_env_id);
CREATE INDEX idx_logical_env_members_physical_id ON logical_environment_members (physical_env_id);
