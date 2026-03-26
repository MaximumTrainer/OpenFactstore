-- CQRS read-optimised views and indexes.
-- These views provide a materialised read-path for the query side of the CQRS
-- architecture while the normalised tables continue to serve the write path.

-- ── Flow Read View ──────────────────────────────────────────────────────────────
CREATE VIEW IF NOT EXISTS v_flows AS
SELECT
    f.id,
    f.name,
    f.description,
    f.required_attestation_types,
    f.org_slug,
    f.template_yaml,
    f.requires_approval,
    f.required_approver_roles,
    f.created_at,
    f.updated_at
FROM flows f;

-- ── Trail Read View ─────────────────────────────────────────────────────────────
CREATE VIEW IF NOT EXISTS v_trails AS
SELECT
    t.id,
    t.flow_id,
    t.git_commit_sha,
    t.git_branch,
    t.git_author,
    t.git_author_email,
    t.pull_request_id,
    t.pull_request_reviewer,
    t.deployment_actor,
    t.status,
    t.org_slug,
    t.template_yaml,
    t.build_url,
    t.created_at,
    t.updated_at
FROM trails t;

-- ── Artifact Read View ──────────────────────────────────────────────────────────
CREATE VIEW IF NOT EXISTS v_artifacts AS
SELECT
    a.id,
    a.trail_id,
    a.image_name,
    a.image_tag,
    a.sha256_digest,
    a.registry,
    a.reported_at,
    a.reported_by,
    a.org_slug
FROM artifacts a;

-- ── Attestation Read View ───────────────────────────────────────────────────────
CREATE VIEW IF NOT EXISTS v_attestations AS
SELECT
    att.id,
    att.trail_id,
    att.type,
    att.status,
    att.evidence_file_hash,
    att.evidence_file_name,
    att.evidence_file_size_bytes,
    att.details,
    att.name,
    att.evidence_url,
    att.org_slug,
    att.artifact_fingerprint,
    att.created_at
FROM attestations att;

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
