-- V3: Immutable audit event log for Runtime Forensics feature.
-- All events are append-only: no UPDATE or DELETE operations are permitted at the application level.

CREATE TABLE audit_events (
    id               UUID         NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    environment_id   UUID,
    trail_id         UUID,
    artifact_sha256  VARCHAR(255),
    actor            VARCHAR(255) NOT NULL,
    payload          TEXT         NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

-- Index for time-range queries (most common access pattern)
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);

-- Index for filtering by event type
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type);

-- Index for trail-scoped audit streams
CREATE INDEX idx_audit_events_trail_id ON audit_events (trail_id) WHERE trail_id IS NOT NULL;

-- Index for actor-based filtering
CREATE INDEX idx_audit_events_actor ON audit_events (actor);
