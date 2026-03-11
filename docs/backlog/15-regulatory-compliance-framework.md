# 15 — Regulatory Compliance Framework (SOX, PCI-DSS, GDPR)

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Implement a regulatory compliance framework that maps fact store attestations and evidence to specific regulatory requirements (SOX, PCI-DSS, GDPR). Enable organizations to define compliance profiles, track regulatory coverage, and generate audit-ready compliance reports aligned to specific regulatory standards.

## Motivation

Financial services organizations must comply with multiple regulatory frameworks simultaneously. The fact store currently tracks generic compliance status (COMPLIANT / NON_COMPLIANT) but does not map evidence to specific regulatory requirements. Auditors need to see:

> *"Show me all PCI-DSS controls and their evidence for Q1 2024."*

This feature bridges the gap between technical compliance evidence and regulatory audit requirements.

## Requirements

### Data Model

#### RegulatoryFramework

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique identifier |
| `name` | String | Framework name (e.g., "PCI-DSS v4.0") |
| `version` | String | Framework version |
| `description` | String | Framework description |
| `controls` | List | List of regulatory controls |
| `isActive` | Boolean | Whether this framework is in use |

#### RegulatoryControl

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique identifier |
| `frameworkId` | UUID | Parent framework |
| `controlId` | String | Official control ID (e.g., "PCI-DSS 6.5.1") |
| `title` | String | Control title |
| `description` | String | Control description |
| `requiredEvidenceTypes` | List | Attestation types that satisfy this control |

#### ComplianceMapping

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique identifier |
| `controlId` | UUID | Regulatory control |
| `flowId` | UUID | Fact store flow |
| `attestationType` | String | Attestation type that maps to this control |
| `isMandatory` | Boolean | Whether this mapping is required for compliance |

#### ComplianceAssessment

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique identifier |
| `frameworkId` | UUID | Regulatory framework |
| `trailId` | UUID | Trail being assessed |
| `assessedAt` | Timestamp | Assessment timestamp |
| `overallStatus` | String | COMPLIANT / NON_COMPLIANT / PARTIAL |
| `controlResults` | List | Per-control compliance results |

### Built-in Framework Templates

Provide pre-configured templates for common financial services regulations:

#### PCI-DSS v4.0

- Requirement 6: Develop and maintain secure systems
  - 6.5.1: Injection vulnerabilities → maps to `SECURITY_SCAN` attestation
  - 6.5.3: Insecure cryptographic storage → maps to `SECURITY_SCAN` attestation
  - 6.5.5: Improper error handling → maps to `CODE_REVIEW` attestation

#### SOX (Sarbanes-Oxley)

- Section 404: Internal controls over financial reporting
  - Change management controls → maps to `APPROVAL` attestation
  - Separation of duties → maps to multi-party `APPROVAL` attestation
  - Audit trail requirements → maps to `AUDIT_LOG` attestation

#### GDPR

- Article 25: Data protection by design
  - Privacy impact assessment → maps to `PRIVACY_REVIEW` attestation
  - Data processing documentation → maps to `DOCUMENTATION` attestation

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/frameworks` | Create a regulatory framework |
| GET | `/api/v1/frameworks` | List all frameworks |
| GET | `/api/v1/frameworks/{id}` | Get framework details with controls |
| POST | `/api/v1/frameworks/{id}/controls` | Add a control to a framework |
| POST | `/api/v1/compliance/mappings` | Create a compliance mapping |
| GET | `/api/v1/compliance/mappings` | List compliance mappings |
| POST | `/api/v1/compliance/assess` | Run a compliance assessment for a trail |
| GET | `/api/v1/compliance/assessments` | List compliance assessments |
| GET | `/api/v1/compliance/assessments/{id}` | Get assessment details |
| GET | `/api/v1/reports/regulatory/{frameworkId}` | Generate regulatory compliance report |

### Compliance Assessment Logic

1. For a given trail and framework, enumerate all controls
2. For each control, check if the required evidence (attestations) exists and has PASSED status
3. Generate a per-control compliance result (SATISFIED / NOT_SATISFIED / PARTIAL)
4. Calculate overall compliance status
5. Persist the assessment for audit trail

### Frontend

- Regulatory Frameworks management page
- Compliance mapping configuration UI
- Trail Detail view shows per-framework compliance status
- Regulatory compliance report generation and export
- Compliance coverage heatmap (which controls are covered)

## Acceptance Criteria

- [ ] RegulatoryFramework and RegulatoryControl entities created
- [ ] ComplianceMapping entity created
- [ ] ComplianceAssessment entity and logic implemented
- [ ] Built-in templates for PCI-DSS, SOX, and GDPR
- [ ] Compliance assessment API endpoints implemented
- [ ] Regulatory compliance report generation
- [ ] Integration with existing AttestationService and ComplianceService
- [ ] Unit tests for compliance assessment logic
- [ ] Frontend regulatory compliance pages
- [ ] OpenAPI documentation updated
