-- #188: Merkle checkpoints over signed event records. Each row covers a contiguous range of
-- sequence numbers; its root is signed with the event-signing key (#184) and published outside
-- the application.
CREATE TABLE IF NOT EXISTS ledger_checkpoints (
    checkpoint_index    BIGINT PRIMARY KEY,
    first_sequence      BIGINT NOT NULL,
    last_sequence       BIGINT NOT NULL,
    leaf_count          INT NOT NULL,
    root_hash           VARCHAR(64) NOT NULL,
    statement           TEXT NOT NULL,
    signature           TEXT NOT NULL,
    signing_key_id      VARCHAR(128) NOT NULL,
    signature_algorithm VARCHAR(32) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_checkpoints_first_sequence UNIQUE (first_sequence)
);
CREATE INDEX IF NOT EXISTS idx_ledger_checkpoints_range ON ledger_checkpoints (first_sequence, last_sequence);
