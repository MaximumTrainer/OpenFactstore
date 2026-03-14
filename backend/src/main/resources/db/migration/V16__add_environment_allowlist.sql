CREATE TABLE environment_allowlist_entries (
    id             UUID         NOT NULL,
    environment_id UUID         NOT NULL,
    sha256         VARCHAR(255),
    name_pattern   VARCHAR(500),
    reason         TEXT         NOT NULL,
    approved_by    VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ,
    status         VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_env_allowlist_entries PRIMARY KEY (id),
    CONSTRAINT fk_env_allowlist_env FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE,
    CONSTRAINT chk_sha256_or_pattern CHECK (sha256 IS NOT NULL OR name_pattern IS NOT NULL)
);
