-- #200: cryptographic shredding. One data key per tenant and month, stored only wrapped by a key
-- in Vault Transit; shredding deletes wrapped_key. Requests need a second administrator.
CREATE TABLE IF NOT EXISTS data_keys (
    id          UUID PRIMARY KEY,
    tenant      VARCHAR(128) NOT NULL,
    period      VARCHAR(7) NOT NULL,
    wrapped_key TEXT,
    kek_id      VARCHAR(128) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    shredded_at TIMESTAMP WITH TIME ZONE,
    shredded_by VARCHAR(255),
    CONSTRAINT uq_data_keys_tenant_period UNIQUE (tenant, period)
);

CREATE TABLE IF NOT EXISTS data_key_shred_requests (
    id           UUID PRIMARY KEY,
    tenant       VARCHAR(128) NOT NULL,
    period       VARCHAR(7) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status       VARCHAR(24) NOT NULL,
    approved_by  VARCHAR(255),
    approved_at  TIMESTAMP WITH TIME ZONE
);
