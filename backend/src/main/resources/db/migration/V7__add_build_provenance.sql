-- V7__add_build_provenance.sql
-- Adds build provenance tracking to artifacts for SLSA compliance.

CREATE TABLE build_provenances (
    id                    UUID         NOT NULL,
    artifact_id           UUID         NOT NULL,
    builder_id            VARCHAR(255) NOT NULL,
    builder_type          VARCHAR(50)  NOT NULL,
    build_config_uri      VARCHAR(512),
    source_repository_uri VARCHAR(512),
    source_commit_sha     VARCHAR(255),
    build_started_on      TIMESTAMPTZ,
    build_finished_on     TIMESTAMPTZ,
    provenance_signature  VARCHAR(2048),
    slsa_level            VARCHAR(10)  NOT NULL DEFAULT 'L0',
    recorded_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_build_provenances PRIMARY KEY (id),
    CONSTRAINT uq_build_provenances_artifact UNIQUE (artifact_id),
    CONSTRAINT fk_build_provenances_artifact FOREIGN KEY (artifact_id) REFERENCES artifacts (id) ON DELETE CASCADE
);
