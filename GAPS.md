# OpenFactstore — Implementation Gaps Report

**Generated:** 2026-03-14  
**Last updated:** 2026-03-14 (post-implementation review)  
**Open issues reviewed:** #24, #43, #74, #77  
**Methodology:** Automated codebase audit cross-referencing each issue's acceptance criteria against implemented source files, migrations, tests, and documentation.

---

## Summary

| Issue | Title | Status | Completeness |
|-------|-------|--------|--------------|
| [#74](#issue-74--kosli-api-model-refactor) | Kosli API Model Refactor | ✅ Complete | 100% |
| [#43](#issue-43--environment-allow-listing) | Allow-listing Third-party Artifacts | ✅ Complete | 100% |
| [#24](#issue-24--grafana--prometheus-metrics) | Grafana Dashboard & Prometheus Metrics | ✅ Complete | 100% |
| [#77](#issue-77--documentation-audit) | Documentation Audit and Synchronization | ✅ Complete | 100% |

---

## Issue #74 — Kosli API Model Refactor

### ✅ Implemented

- `Attestation` domain entity with `name`, `type`, `evidenceUrl`, `orgSlug`, `artifactFingerprint` fields
- `AttestationResponse` includes `compliant` boolean (derived from `status == PASSED`) and `artifactFingerprint`
- Kosli v2 endpoint: `POST /api/v2/attestations/{org}/{flow}/{trail}/{artifactFingerprint}`
- `KosliV2AttestationController` validates `org` (via `IOrganisationRepository.existsBySlug`) and `flow` (via `IFlowRepository.findAllByOrgSlug`) — throws `NotFoundException` if either is missing
- `artifactFingerprint` path variable is persisted to `attestations.artifact_fingerprint` (Flyway V26)
- `IAttestationRepository.findByArtifactFingerprint()` method added
- Backward-compatible v1 endpoint `/api/v1/trails/{trailId}/attestations` still works
- v1 endpoint marked `deprecated = true` in OpenAPI spec
- Contract test (Pact) exists: `pacts/kosli-cli-factstore-backend.json`

### Remaining minor items

- **Pact coverage** — contract test has one happy-path case. Additional cases (failed status, 404 on bad org/flow, fingerprint in response) would increase confidence but are non-blocking.

---

## Issue #43 — Environment Allow-listing

### ✅ Implemented

- `EnvironmentAllowlistEntry` entity with all required fields: `sha256`, `namePattern`, `reason`, `approvedBy`, `createdAt`, `expiresAt`, `status` (ACTIVE/REMOVED)
- `isEffective(now)` and `matches(sha256, name)` methods on the entity
- Flyway migration V16 creates `environment_allowlist_entries` table with correct constraints
- REST API: `POST`, `GET`, `DELETE /api/v1/environments/{id}/allowlist`
- `DELETE` performs a soft delete (marks entry as `REMOVED`, not physically deleted)
- Expired entries (past `expiresAt`) correctly return `isEffective = false`
- `EnvironmentAllowlistService.isAllowlisted()` is called from `DeploymentPolicyService.evaluateGate()` before any policy checks — allow-listed artifacts receive `GateDecision.ALLOWED` immediately
- Comprehensive unit tests: SHA match, regex match, expiry, double-remove conflict

### Remaining minor items

- **Integration tests for gate bypass** — no test places an artifact on the allowlist and verifies it bypasses the gate end-to-end.

---

## Issue #24 — Grafana Dashboard & Prometheus Metrics

### ✅ Implemented

- `micrometer-registry-prometheus` dependency in `build.gradle.kts`
- `/actuator/prometheus` endpoint enabled in `application.yml`
- `ComplianceMetricsService` registers 12 custom Micrometer gauges including all previously missing metrics:
  - `factstore_gate_evaluations_total` (Counter — incremented in `DeploymentPolicyService`)
  - `factstore_gate_blocked_total` (Counter — incremented in `DeploymentPolicyService`)
  - `factstore_gate_allowed_total` (Counter — incremented in `DeploymentPolicyService`)
  - `factstore_approvals_pending` (Gauge — live count via `IApprovalRepository`)
  - `factstore_drift_detected` (Gauge — live count via `IDriftReportRepository`)
- `GET /api/v1/metrics/compliance` and `GET /api/v1/metrics/security` JSON endpoints
- `monitoring/prometheus.yml` scrape configuration
- `monitoring/provisioning/` Grafana datasource and dashboard provisioning YAML
- 4 Grafana dashboard JSON templates: compliance-overview, security-scans, deployment-gates, audit-forensics
- Docker Compose updated with `prometheus` and `grafana` services
- Unit tests for `ComplianceMetricsService` covering all registered metrics
- "Monitoring ↗" external link to Grafana (`http://localhost:3000`) added to `NavBar.vue`

---

## Issue #77 — Documentation Audit and Synchronization

### ✅ Implemented

- `README.md` — Spring Boot version corrected from `3.2.5` to `3.4.13`
- `DEPLOY.md` — setup timing updated from "2 minutes" to "3–5 minutes"
- `DEPLOY.md` — full environment variable reference table added for PostgreSQL, Vault, Grafana, and GitHub OAuth variables
- `docs/getting-started/03-authentication.md` — rewritten from "Coming Soon" to describe the implemented service accounts and API keys feature
- `docs/getting-started/08-environments.md` — rewritten from "Coming Soon" to describe the implemented environments, snapshots, drift detection, and allow-listing features
- `docs/getting-started/09-policies.md` — rewritten from "Coming Soon" to describe the implemented deployment policies and gate evaluation features
- `docs/getting-started/10-approvals.md` — rewritten from "Coming Soon" to describe the implemented approval workflow
- `ROADMAP.md` — created with current feature state and near/medium/long-term planned work
