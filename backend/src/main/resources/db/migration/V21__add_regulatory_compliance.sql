CREATE TABLE regulatory_frameworks (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    org_slug VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE regulatory_controls (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    framework_id UUID NOT NULL,
    control_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    required_evidence_types TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE compliance_mappings (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    control_id UUID NOT NULL,
    flow_id UUID NOT NULL,
    attestation_type VARCHAR(100) NOT NULL,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE compliance_assessments (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    framework_id UUID NOT NULL,
    trail_id UUID NOT NULL,
    overall_status VARCHAR(20) NOT NULL,
    control_results_json TEXT,
    org_slug VARCHAR(255),
    assessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
