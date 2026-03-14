CREATE TABLE opa_bundles (
    id         UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    version    VARCHAR(50)  NOT NULL,
    rego_content TEXT       NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE',
    org_slug   VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_opa_bundles PRIMARY KEY (id)
);

CREATE TABLE policy_decisions (
    id           UUID    NOT NULL,
    bundle_id    UUID,
    input_json   TEXT    NOT NULL,
    result_allow BOOLEAN NOT NULL,
    deny_reasons TEXT,
    org_slug     VARCHAR(255),
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_policy_decisions PRIMARY KEY (id)
);
