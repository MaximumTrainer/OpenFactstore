-- #184: every stored event record is signed by a key held in a key service, over a versioned
-- canonical encoding that links it to the record before it.
ALTER TABLE domain_events ADD COLUMN signature TEXT;
ALTER TABLE domain_events ADD COLUMN signature_algorithm VARCHAR(32);
ALTER TABLE domain_events ADD COLUMN signing_key_id VARCHAR(128);
ALTER TABLE domain_events ADD COLUMN canonical_version INT;
ALTER TABLE domain_events ADD COLUMN prev_hash VARCHAR(64);
ALTER TABLE domain_events ADD COLUMN record_hash VARCHAR(64);
-- SIGNED, or UNSIGNED for a record stored before signing existed.
ALTER TABLE domain_events ADD COLUMN signature_status VARCHAR(16) DEFAULT 'UNSIGNED' NOT NULL;
-- When an UNSIGNED record was flagged: the time this migration ran.
ALTER TABLE domain_events ADD COLUMN unsigned_since TIMESTAMP WITH TIME ZONE;
UPDATE domain_events SET unsigned_since = CURRENT_TIMESTAMP WHERE signature_status = 'UNSIGNED';
CREATE INDEX idx_domain_events_signature_status ON domain_events (signature_status, sequence_number);

-- The newest signed record. Appends lock this row, so records are chained in sequence order,
-- and verification compares against it to detect a deleted newest record.
CREATE TABLE event_chain_head (
    id            INT PRIMARY KEY,
    last_sequence BIGINT,
    last_hash     VARCHAR(64) NOT NULL
);
INSERT INTO event_chain_head (id, last_sequence, last_hash)
VALUES (1, NULL, '0000000000000000000000000000000000000000000000000000000000000000');
