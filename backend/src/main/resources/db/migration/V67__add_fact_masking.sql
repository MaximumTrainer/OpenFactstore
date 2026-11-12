-- #198: masking at the ingestion boundary. The ruleset a fact was checked against, what Factstore
-- masked on arrival (redact mode), and the hash of the signed body when masking means the body
-- itself is not stored.
ALTER TABLE facts ADD COLUMN signed_body_sha256 VARCHAR(64);
ALTER TABLE facts ADD COLUMN masking_ruleset VARCHAR(64);
ALTER TABLE facts ADD COLUMN masked_paths TEXT;
