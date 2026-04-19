ALTER TABLE artifacts ADD COLUMN artifact_type VARCHAR(20);
ALTER TABLE artifacts ADD COLUMN build_url VARCHAR(2048);
ALTER TABLE artifacts ADD COLUMN commit_url VARCHAR(2048);
