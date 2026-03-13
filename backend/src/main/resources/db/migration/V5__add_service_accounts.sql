-- V5: Service accounts and updated API keys schema

-- Service accounts table (machine identities for CI/CD pipelines)
CREATE TABLE service_accounts (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_service_accounts PRIMARY KEY (id),
    CONSTRAINT uq_service_accounts_name UNIQUE (name)
);

-- Migrate api_keys to support polymorphic ownership (user or service account)
-- Drop the hard user FK constraint (ownership is now polymorphic)
ALTER TABLE api_keys DROP CONSTRAINT fk_api_keys_user;

-- Rename user_id -> owner_id (polymorphic: references users or service_accounts)
ALTER TABLE api_keys RENAME COLUMN user_id TO owner_id;

-- Rename name -> label
ALTER TABLE api_keys RENAME COLUMN name TO label;

-- Convert type column: existing values PERSONAL -> USER, SERVICE -> SERVICE_ACCOUNT
-- These UPDATEs handle any existing rows in production; on a fresh database they are no-ops.
UPDATE api_keys SET type = 'USER' WHERE type = 'PERSONAL';
UPDATE api_keys SET type = 'SERVICE_ACCOUNT' WHERE type = 'SERVICE';
ALTER TABLE api_keys RENAME COLUMN type TO owner_type;

-- Add optional TTL / expiry columns
ALTER TABLE api_keys ADD COLUMN ttl_days  INT;
ALTER TABLE api_keys ADD COLUMN expires_at TIMESTAMPTZ;
