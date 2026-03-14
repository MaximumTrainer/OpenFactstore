CREATE TABLE security_scan_results (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    trail_id UUID NOT NULL,
    attestation_id UUID,
    tool VARCHAR(100) NOT NULL,
    tool_version VARCHAR(50),
    scan_type VARCHAR(20),
    target VARCHAR(500),
    critical_vulnerabilities INT NOT NULL DEFAULT 0,
    high_vulnerabilities INT NOT NULL DEFAULT 0,
    medium_vulnerabilities INT NOT NULL DEFAULT 0,
    low_vulnerabilities INT NOT NULL DEFAULT 0,
    informational INT NOT NULL DEFAULT 0,
    scan_duration_seconds BIGINT,
    report_url VARCHAR(500),
    org_slug VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE security_thresholds (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    flow_id UUID NOT NULL UNIQUE,
    max_critical INT NOT NULL DEFAULT 0,
    max_high INT NOT NULL DEFAULT 0,
    max_medium INT NOT NULL DEFAULT 10,
    max_low INT NOT NULL DEFAULT 2147483647,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
