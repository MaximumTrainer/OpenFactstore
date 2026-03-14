ALTER TABLE attestations ADD COLUMN artifact_fingerprint VARCHAR(255);

CREATE INDEX idx_attestations_artifact_fingerprint ON attestations(artifact_fingerprint);
