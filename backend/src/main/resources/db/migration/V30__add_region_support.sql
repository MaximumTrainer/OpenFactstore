ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS region VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_audit_events_region ON audit_events (region);
