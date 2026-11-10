-- #187: the trusted registry of CI runners' signing keys, and each fact's source signature.
CREATE TABLE IF NOT EXISTS workload_signing_keys (
    id          UUID PRIMARY KEY,
    key_id      VARCHAR(128) NOT NULL,
    public_key  TEXT NOT NULL,
    status      VARCHAR(16) NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP,
    CONSTRAINT uq_workload_signing_keys_key_id UNIQUE (key_id)
);

-- Facts ingested before #187 were not signed; these stay null for them.
ALTER TABLE facts ADD COLUMN signature_key_id VARCHAR(128);
ALTER TABLE facts ADD COLUMN signature TEXT;
ALTER TABLE facts ADD COLUMN signed_body TEXT;
