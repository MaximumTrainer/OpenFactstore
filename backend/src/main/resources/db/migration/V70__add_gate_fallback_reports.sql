-- #193: gate decisions a client made by risk tier while Factstore was unreachable, reported once
-- it was reachable again. report_id is the client's, so a resent report is recorded only once.
CREATE TABLE IF NOT EXISTS gate_fallback_reports (
    report_id   UUID PRIMARY KEY,
    audit_id    UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
