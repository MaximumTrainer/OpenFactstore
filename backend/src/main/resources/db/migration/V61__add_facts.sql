-- #196: facts accepted at ingress after strict validation against the schema they name.
CREATE TABLE IF NOT EXISTS facts (
    fact_id            UUID PRIMARY KEY,
    schema_ref         VARCHAR(160) NOT NULL,
    subject_sha256     VARCHAR(64) NOT NULL,
    subject_repository VARCHAR(255),
    payload_json       TEXT NOT NULL,
    ingested_by        VARCHAR(255) NOT NULL,
    ingested_at        TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_facts_subject_sha256 ON facts (subject_sha256);
