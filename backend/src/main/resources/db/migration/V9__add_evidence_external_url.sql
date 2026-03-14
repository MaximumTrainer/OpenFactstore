-- Make content nullable to support external-URL evidence references,
-- and add external_url column for storing the pointer to externally-hosted files.
ALTER TABLE evidence_files
    ALTER COLUMN content DROP NOT NULL;

ALTER TABLE evidence_files
    ADD COLUMN IF NOT EXISTS external_url TEXT;
