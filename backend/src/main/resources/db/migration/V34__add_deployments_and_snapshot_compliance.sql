CREATE TABLE deployments (
    id UUID PRIMARY KEY,
    artifact_sha256 VARCHAR(255) NOT NULL,
    environment_id UUID NOT NULL,
    snapshot_index BIGINT NOT NULL,
    deployed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_deployments_artifact ON deployments(artifact_sha256);
CREATE INDEX idx_deployments_env ON deployments(environment_id);

ALTER TABLE snapshot_artifacts ADD COLUMN compliance_state VARCHAR(20);
