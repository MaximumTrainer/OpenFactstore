-- V8: SSO (Single Sign-On) configuration per organisation.
-- Supports OIDC-based providers: Microsoft Entra ID (Azure AD) and Okta.

CREATE TABLE sso_configs (
    id                  UUID         NOT NULL,
    org_slug            VARCHAR(255) NOT NULL,
    provider            VARCHAR(50)  NOT NULL,
    issuer_url          TEXT         NOT NULL,
    client_id           VARCHAR(255) NOT NULL,
    -- client_secret stored here; protect at rest via secrets management in production.
    client_secret       TEXT,
    attribute_mappings  TEXT         NOT NULL DEFAULT '{"email":"email","name":"name"}',
    group_role_mappings TEXT         NOT NULL DEFAULT '{}',
    is_mandatory        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_sso_configs PRIMARY KEY (id),
    -- The UNIQUE constraint already creates an index on org_slug in PostgreSQL.
    CONSTRAINT uq_sso_configs_org_slug UNIQUE (org_slug)
);
