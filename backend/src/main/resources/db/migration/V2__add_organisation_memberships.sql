-- V2: User accounts, API keys, and organisation membership table for RBAC

CREATE TABLE users (
    id         UUID         NOT NULL,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    github_id  VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_github_id UNIQUE (github_id)
);

CREATE TABLE api_keys (
    id           UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    key_prefix   VARCHAR(12)  NOT NULL,
    hashed_key   VARCHAR(255) NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT pk_api_keys PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE organisation_memberships (
    id        UUID        NOT NULL,
    org_slug  VARCHAR(255) NOT NULL,
    user_id   UUID        NOT NULL,
    role      VARCHAR(50) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_org_memberships PRIMARY KEY (id),
    CONSTRAINT uq_org_memberships_org_user UNIQUE (org_slug, user_id),
    CONSTRAINT fk_org_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
