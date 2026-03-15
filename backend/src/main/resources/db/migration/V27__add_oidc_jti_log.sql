CREATE TABLE oidc_jti_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti VARCHAR(255) NOT NULL UNIQUE,
    issuer VARCHAR(255) NOT NULL,
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_oidc_jti_log_jti ON oidc_jti_log (jti);
