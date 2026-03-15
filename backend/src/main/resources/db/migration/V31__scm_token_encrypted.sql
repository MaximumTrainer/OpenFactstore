-- Track whether SCM integration tokens are AES-256-GCM encrypted.
-- FALSE for tokens created before encryption was introduced.
ALTER TABLE scm_integrations ADD COLUMN IF NOT EXISTS is_token_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
