-- #185: a failed post-commit publish must not be dropped.
--
-- Each committed domain event is recorded here in the same transaction as its domain_events
-- row, and removed once the publish to the event bus succeeds. Whatever remains is a fact the
-- query side has not heard about; EventOutboxRelay re-publishes it. domain_events itself stays
-- append-only: publication state lives in this table instead.
CREATE TABLE IF NOT EXISTS event_outbox (
    sequence_number BIGINT PRIMARY KEY,
    enqueued_at     TIMESTAMP NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    last_attempt_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_event_outbox_enqueued_at ON event_outbox (enqueued_at);
