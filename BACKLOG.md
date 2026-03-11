# Factstore Backlog — Financial Services Compliance Features

This backlog tracks all features required to build a **compliance-grade Fact Store** for financial services, supporting automated compliance tracking, audit readiness, and regulatory enforcement (SOX, PCI-DSS, GDPR).

> **Legend:**
> - ✅ **Existing** — A GitHub issue already covers this requirement
> - 🆕 **New** — A new backlog item created from the financial services requirements

---

## Backlog Summary

| # | Feature | Status | GitHub Issue | Details |
|---|---------|--------|-------------|---------|
| 1 | Persistent Storage (PostgreSQL) | ✅ Existing | [#12](https://github.com/MaximumTrainer/Factstore/issues/12) | [View](docs/backlog/01-persistent-storage.md) |
| 2 | Release Approval Workflow | ✅ Existing | [#6](https://github.com/MaximumTrainer/Factstore/issues/6) | [View](docs/backlog/02-release-approval-workflow.md) |
| 3 | Deployment Gate & Policy Engine | ✅ Existing | [#7](https://github.com/MaximumTrainer/Factstore/issues/7) | [View](docs/backlog/03-deployment-gate-policy-engine.md) |
| 4 | Runtime Forensics & Immutable Audit Log | ✅ Existing | [#8](https://github.com/MaximumTrainer/Factstore/issues/8) | [View](docs/backlog/04-immutable-audit-log.md) |
| 5 | CI/CD Pipeline Event Webhooks | ✅ Existing | [#10](https://github.com/MaximumTrainer/Factstore/issues/10) | [View](docs/backlog/05-cicd-pipeline-webhooks.md) |
| 6 | Enhanced Query & Reporting API | ✅ Existing | [#11](https://github.com/MaximumTrainer/Factstore/issues/11) | [View](docs/backlog/06-query-reporting-api.md) |
| 7 | Artifact Build Provenance | ✅ Existing | [#5](https://github.com/MaximumTrainer/Factstore/issues/5) | [View](docs/backlog/07-artifact-build-provenance.md) |
| 8 | Notifications & Compliance Alerts | ✅ Existing | [#13](https://github.com/MaximumTrainer/Factstore/issues/13) | [View](docs/backlog/08-notifications-compliance-alerts.md) |
| 9 | Environment Tracking & Snapshots | ✅ Existing | [#4](https://github.com/MaximumTrainer/Factstore/issues/4) | [View](docs/backlog/09-environment-tracking.md) |
| 10 | Environment Diff & Drift Detection | ✅ Existing | [#9](https://github.com/MaximumTrainer/Factstore/issues/9) | [View](docs/backlog/10-drift-detection.md) |
| 11 | HashiCorp Vault Integration | 🆕 New | — | [View](docs/backlog/11-hashicorp-vault-integration.md) |
| 12 | OPA (Open Policy Agent) Integration | 🆕 New | — | [View](docs/backlog/12-opa-integration.md) |
| 13 | Jira & Confluence Integration | 🆕 New | — | [View](docs/backlog/13-jira-confluence-integration.md) |
| 14 | Grafana Dashboard Integration | 🆕 New | — | [View](docs/backlog/14-grafana-dashboard-integration.md) |
| 15 | Regulatory Compliance Framework | 🆕 New | — | [View](docs/backlog/15-regulatory-compliance-framework.md) |
| 16 | Security Scan Integration (OWASP) | 🆕 New | — | [View](docs/backlog/16-security-scan-integration.md) |
| 17 | Continuous Evidence Collection Pipeline | 🆕 New | — | [View](docs/backlog/17-continuous-evidence-collection.md) |
| 18 | Immutable Ledger Support | 🆕 New | — | [View](docs/backlog/18-immutable-ledger-support.md) |

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                     CI/CD Pipelines                              │
│         (GitHub Actions / Jenkins / CircleCI)                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ Security │  │  Test    │  │  Compliance  │  │   Build    │  │
│  │  Scans   │  │ Results  │  │   Checks     │  │ Provenance │  │
│  └────┬─────┘  └────┬─────┘  └──────┬───────┘  └─────┬──────┘  │
│       │              │               │                │         │
└───────┼──────────────┼───────────────┼────────────────┼─────────┘
        │              │               │                │
        ▼              ▼               ▼                ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Fact Store API                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│  │  Webhooks   │  │  Evidence    │  │  Compliance          │    │
│  │  (Inbound)  │  │  Vault API   │  │  Verification API    │    │
│  └──────┬──────┘  └──────┬───────┘  └──────────┬───────────┘    │
│         │                │                      │               │
│         ▼                ▼                      ▼               │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Core Domain (Hexagonal Architecture)        │   │
│  │  ┌──────┐ ┌───────┐ ┌───────────┐ ┌──────────────────┐  │   │
│  │  │Flows │ │Trails │ │Attestation│ │ Policy Engine    │  │   │
│  │  └──────┘ └───────┘ └───────────┘ │ (OPA / Internal) │  │   │
│  │                                    └──────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│         │                │                      │               │
│         ▼                ▼                      ▼               │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                 Persistence & Storage                     │   │
│  │  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐   │   │
│  │  │PostgreSQL│  │HashiCorp     │  │ Immutable Ledger │   │   │
│  │  │(Facts)   │  │Vault (Secrets│  │ (Audit Trail)    │   │   │
│  │  └──────────┘  └──────────────┘  └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
        │                │                      │
        ▼                ▼                      ▼
┌──────────────────────────────────────────────────────────────────┐
│                   External Integrations                          │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ Grafana  │  │ Jira /       │  │ Notification Channels    │   │
│  │Dashboard │  │ Confluence   │  │ (Slack, Webhooks, Email) │   │
│  └──────────┘  └──────────────┘  └──────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Fact Structure (JSON Example)

Each fact represents a verifiable piece of evidence associated with a software release, security check, or compliance event. The example below is a **conceptual schema** illustrating the data model — the actual REST API uses camelCase field names and UUID identifiers consistent with the Factstore DTOs:

```json
{
  "factId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2025-02-20T14:05:30Z",
  "system": "CI/CD Pipeline",
  "eventType": "Security Scan",
  "relatedEntity": {
    "type": "software_release",
    "id": "release-v1.2.3"
  },
  "status": "PASSED",
  "details": {
    "tool": "OWASP ZAP",
    "criticalVulnerabilities": 0,
    "highVulnerabilities": 2,
    "mediumVulnerabilities": 5,
    "lowVulnerabilities": 10
  },
  "approvals": [
    {
      "approver": "security-team@company.com",
      "timestamp": "2025-02-20T15:00:00Z"
    }
  ],
  "evidenceUrl": "https://evidence-store.company.com/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

---

## Storage Format Options

| Storage Type | Description | Example Technologies |
|---|---|---|
| JSON Document Store | Stores facts as JSON objects | MongoDB, Amazon DynamoDB, Elasticsearch |
| Relational Database (SQL) | Stores structured fact records with relationships | PostgreSQL, MySQL, AWS RDS |
| Immutable Ledger DB | Stores tamper-proof facts for auditability | AWS QLDB, Hyperledger Fabric |
| Blob Storage | Stores evidence files (PDF, logs) with metadata | AWS S3, Azure Blob Storage, Google Cloud Storage |

---

## Getting Started

See individual backlog items in [`docs/backlog/`](docs/backlog/) for detailed requirements, acceptance criteria, and implementation notes.
