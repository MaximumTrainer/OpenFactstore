-- #251: per-organisation retention policies by data class, and legal holds that suspend them.
CREATE TABLE IF NOT EXISTS retention_policies (
    id          UUID PRIMARY KEY,
    tenant      VARCHAR(255) NOT NULL,
    data_class  VARCHAR(64)  NOT NULL,
    retain_days INTEGER      NOT NULL,
    updated_by  VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_retention_policy UNIQUE (tenant, data_class)
);

CREATE TABLE IF NOT EXISTS legal_holds (
    id          UUID PRIMARY KEY,
    scope_type  VARCHAR(16)  NOT NULL,
    scope_id    UUID         NOT NULL,
    reason      TEXT         NOT NULL,
    placed_by   VARCHAR(255) NOT NULL,
    placed_at   TIMESTAMP    NOT NULL,
    released_by VARCHAR(255),
    released_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_legal_holds_active ON legal_holds (released_at);
