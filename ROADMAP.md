# Factstore Roadmap

This document outlines the planned evolution of Factstore. Features are grouped by theme rather than strict release milestones, as priorities may shift based on community feedback.

---

## Currently Implemented

- ✅ **Flows, Trails, Artifacts** — core supply chain data model
- ✅ **Attestations** — record and query compliance evidence (Kosli v1 + v2 API)
- ✅ **Organisations & Multi-tenancy** — org-scoped resources with membership management
- ✅ **Service Accounts & API Keys** — machine authentication for CI/CD pipelines
- ✅ **Deployment Policies & Gate** — define compliance requirements and block non-compliant deployments
- ✅ **Release Approvals** — formal sign-off workflow with audit trail
- ✅ **Environments & Snapshots** — track what is running across K8S, ECS, Lambda, Docker environments
- ✅ **Drift Detection** — compare environment snapshots and detect unauthorised changes
- ✅ **Allow-listing** — bypass policy checks for pre-approved third-party artifacts
- ✅ **Pull Request Attestations** — record PR existence as compliance evidence via SCM API
- ✅ **Security Scan Integration** — record OWASP ZAP, Snyk, Trivy, Dependency-Check results
- ✅ **Build Provenance** — SLSA-compatible build provenance recording
- ✅ **OPA Integration** — optional external policy evaluation via Open Policy Agent
- ✅ **Regulatory Compliance Framework** — map attestations to SOX, PCI-DSS, GDPR controls
- ✅ **Prometheus Metrics** — Micrometer counters and gauges exposed at `/actuator/prometheus`
- ✅ **Grafana Dashboards** — pre-built dashboard templates for compliance and security metrics
- ✅ **Audit Log** — immutable event log for every state-changing operation
- ✅ **Notifications & Alerts** — webhook-based and in-app notification rules
- ✅ **Flow Tags** — key/value tagging for flows
- ✅ **Dry-run Mode** — validate writes without persisting (`X-Factstore-Dry-Run: true`)
- ✅ **Evidence Vault** — store and retrieve binary compliance evidence (reports, logs)
- ✅ **Logical Environments** — group physical environments into named logical groups

---

## Near-term

### CLI / Developer Experience
- **Factstore CLI** (#33) — a native command-line tool so developers do not need to write raw `curl` commands in their pipelines. Will support: `factstore attest`, `factstore trail create`, `factstore gate evaluate`, etc.
- **CI/CD Integration Helpers** (#46) — pre-built GitHub Actions, GitLab CI, Jenkins, and CircleCI integration examples and templates.

### Policy Improvements
- **Continuous Evidence Collection Pipeline** (#27) — automated bulk evidence collection, commit-SHA correlation, and gap detection to minimise manual attestation work.
- **Flow Template YAML Spec improvements** (#35) — richer per-artifact attestation requirements defined in YAML rather than a flat list.

---

## Medium-term

### Scalability
- **PostgreSQL as primary database** — migrate from H2 in-memory to PostgreSQL as the default persistence layer for production deployments.
- **Event streaming** — publish supply chain events to Kafka or SNS for downstream consumption.

### Integrations
- **Vault integration** — store sensitive credentials (SCM tokens, signing keys) in HashiCorp Vault.
- **Cosign / Sigstore** — verify container image signatures as part of gate evaluation.
- **OIDC token attestation** — accept GitHub Actions OIDC tokens as provenance evidence.

---

## Longer-term / Community Requests

- **Multi-region replication** — active-active deployment for globally distributed teams.
- **WASM policy plugins** — alternative to OPA for lightweight policy evaluation at the edge.
- **Factstore Hub** — a shared registry of reusable policy templates and regulatory frameworks.
- **GraphQL API** — an optional GraphQL layer alongside the existing REST API.

---

## Contributing

Have an idea? Open an issue at [github.com/MaximumTrainer/OpenFactstore/issues](https://github.com/MaximumTrainer/OpenFactstore/issues) with the `enhancement` label. Pull requests are welcome — see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.
