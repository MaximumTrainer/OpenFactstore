-- Event Sourcing: append-only domain event log.
-- This table is the single source of truth for every state change.
-- It is immutable — rows are only ever inserted, never updated or deleted.

CREATE TABLE domain_events (
    sequence_number BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        UUID         NOT NULL UNIQUE,
    aggregate_id    UUID         NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    metadata        TEXT,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_domain_events_aggregate    ON domain_events (aggregate_id, sequence_number);
CREATE INDEX idx_domain_events_type         ON domain_events (aggregate_type, sequence_number);
CREATE INDEX idx_domain_events_event_type   ON domain_events (event_type, sequence_number);
CREATE INDEX idx_domain_events_occurred_at  ON domain_events (occurred_at);
