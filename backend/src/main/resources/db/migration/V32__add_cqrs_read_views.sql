-- CQRS read-optimized indexes.
-- Speed up the most common query-side access patterns without introducing
-- unused schema objects.  Views can be added later when the read-path
-- repositories are wired to query them directly.

-- ── Read-path indexes (speed up common queries) ────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_trails_flow_id ON trails (flow_id);
CREATE INDEX IF NOT EXISTS idx_trails_status ON trails (status);
CREATE INDEX IF NOT EXISTS idx_trails_created_at ON trails (created_at);
CREATE INDEX IF NOT EXISTS idx_artifacts_trail_id ON artifacts (trail_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_sha256 ON artifacts (sha256_digest);
CREATE INDEX IF NOT EXISTS idx_attestations_trail_id ON attestations (trail_id);
CREATE INDEX IF NOT EXISTS idx_attestations_type ON attestations (type);
CREATE INDEX IF NOT EXISTS idx_attestations_fingerprint ON attestations (artifact_fingerprint);
CREATE INDEX IF NOT EXISTS idx_flows_org_slug ON flows (org_slug);
