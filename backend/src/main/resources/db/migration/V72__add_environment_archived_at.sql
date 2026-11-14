-- #245: environments are archived, never deleted, so their snapshot history is kept.
ALTER TABLE environments ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
