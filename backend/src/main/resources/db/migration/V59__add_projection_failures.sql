-- #185: a domain event the read-model projector cannot apply is no longer acknowledged as
-- success and forgotten. It is kept here, with its payload, so it can be inspected and replayed.
CREATE TABLE IF NOT EXISTS projection_failures (
    id              UUID PRIMARY KEY,
    sequence_number BIGINT NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         TEXT NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    failed_at       TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_projection_failures_sequence_number ON projection_failures (sequence_number);
CREATE INDEX IF NOT EXISTS ix_projection_failures_failed_at ON projection_failures (failed_at);
