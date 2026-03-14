# OpenFactstore — Implementation Gaps Report

**Generated:** 2026-03-14  
**Open issues reviewed:** #24, #43, #74, #77  
**Methodology:** Automated codebase audit cross-referencing each issue's acceptance criteria against implemented source files, migrations, tests, and documentation.

---

## Summary

| Issue | Title | Status | Completeness |
|-------|-------|--------|--------------|
| [#74](#issue-74--kosli-api-model-refactor) | Kosli API Model Refactor | ⚠️ Partial | ~75% |
| [#43](#issue-43--environment-allow-listing) | Allow-listing Third-party Artifacts | ⚠️ Partial | ~70% |
| [#24](#issue-24--grafana--prometheus-metrics) | Grafana Dashboard & Prometheus Metrics | ⚠️ Partial | ~85% |
| [#77](#issue-77--documentation-audit) | Documentation Audit and Synchronization | ❌ Not started | ~0% |

---

## Issue #74 — Kosli API Model Refactor

### ✅ Implemented

- `Attestation` domain entity with `name`, `type`, `evidenceUrl`, `orgSlug` fields
- `AttestationResponse` includes `compliant` boolean (derived from `status == PASSED`)
- Kosli v2 endpoint: `POST /api/v2/attestations/{org}/{flow}/{trail}/{artifactFingerprint}`
- Backward-compatible v1 endpoint `/api/v1/trails/{trailId}/attestations` still works
- Flyway migration V9 adds `name` and `evidence_url` columns to `attestations` table
- Contract test (Pact) exists: `pacts/kosli-cli-factstore-backend.json`
- API Reference documents both v1 and v2 endpoints

### ❌ Gaps

#### CRITICAL — Artifact fingerprint is accepted but not persisted

The `artifactFingerprint` path variable in `KosliV2AttestationController` is accepted but silently discarded — it is never passed to the service or stored in the database. The `attestations` table has no `artifact_fingerprint` column.

**Impact:** It is impossible to query attestations by artifact fingerprint, breaking the core Kosli audit-trail chain (`Flow → Trail → Artifact → Attestation`).

**Fix required:**
1. Add a Flyway migration to add `artifact_fingerprint VARCHAR(255)` to `attestations`
2. Add `artifactFingerprint: String?` to the `Attestation` entity
3. Update `KosliV2AttestationController` to pass the fingerprint to `AttestationService`
4. Update `IAttestationRepository` with `findByArtifactFingerprint()` method

#### CRITICAL — `org` and `flow` path params not validated

The v2 endpoint accepts `org` and `flow` as path variables but does not validate that they exist or that the attestation is scoped to them. There is no multi-tenancy enforcement on the Kosli endpoint.

**Fix required:** Look up the org by slug and the flow by name; throw `NotFoundException` if either is missing.

#### Minor — Contract test coverage is minimal

The Pact file contains only one test case (happy path, `status=PASSED`). Missing:
- `compliant=false` when `status=FAILED`
- Invalid org/flow returns 404
- Artifact fingerprint is included in response

#### Minor — No deprecation signals on v1 endpoint

The v1 attestation endpoint has no deprecation headers, warnings, or documentation notes pointing consumers to v2.

---

## Issue #43 — Environment Allow-listing

### ✅ Implemented

- `EnvironmentAllowlistEntry` entity with all required fields: `sha256`, `namePattern`, `reason`, `approvedBy`, `createdAt`, `expiresAt`, `status` (ACTIVE/REMOVED)
- `isEffective(now)` and `matches(sha256, name)` methods on the entity
- Flyway migration V16 creates `environment_allowlist_entries` table with correct constraints
- REST API: `POST`, `GET`, `DELETE /api/v1/environments/{id}/allowlist`
- `DELETE` performs a soft delete (marks entry as `REMOVED`, not physically deleted)
- Expired entries (past `expiresAt`) correctly return `isEffective = false`
- Comprehensive unit tests: SHA match, regex match, expiry, double-remove conflict

### ❌ Gaps

#### CRITICAL — Allowlist is not integrated into deployment gate evaluation

`EnvironmentAllowlistService.isAllowlisted()` exists and is correct, but it is **never called** from `DeploymentPolicyService.evaluateGate()`. An artifact that is on the allowlist still goes through policy evaluation and will be blocked if it has no attestations.

**Fix required:** In `DeploymentPolicyService.evaluateGate()`, call `allowlistService.isAllowlisted(environmentId, artifactSha256, artifactName)` before policy checks. If the artifact matches, return `GateDecision.ALLOWED` immediately with reason `"artifact is allowlisted"`.

#### Minor — No integration tests for gate bypass

There are no tests that place an artifact on the allowlist and verify it bypasses the deployment gate.

---

## Issue #24 — Grafana Dashboard & Prometheus Metrics

### ✅ Implemented

- `micrometer-registry-prometheus` dependency in `build.gradle.kts`
- `/actuator/prometheus` endpoint enabled in `application.yml`
- `ComplianceMetricsService` registers 10 custom Micrometer gauges
- `GET /api/v1/metrics/compliance` and `GET /api/v1/metrics/security` JSON endpoints
- `monitoring/prometheus.yml` scrape configuration
- `monitoring/provisioning/` Grafana datasource and dashboard provisioning YAML
- 4 Grafana dashboard JSON templates: compliance-overview, security-scans, deployment-gates, audit-forensics
- Docker Compose updated with `prometheus` and `grafana` services
- Unit tests for `ComplianceMetricsService` covering 10 registered metrics

### ❌ Gaps

#### Missing metrics (5 of 14 required not implemented)

The following metrics specified in the issue are absent from `ComplianceMetricsService`:

| Metric | Description |
|--------|-------------|
| `factstore_gate_evaluations_total` | Total gate evaluations performed |
| `factstore_gate_blocked_total` | Deployments blocked by gate |
| `factstore_gate_allowed_total` | Deployments allowed by gate |
| `factstore_approvals_pending` | Current pending approval count |
| `factstore_drift_detected` | Environments with active drift |

**Fix required:** Add these as Micrometer `Counter` (for totals) and `Gauge` (for approvals_pending and drift_detected) in `ComplianceMetricsService`. Increment the counters from `DeploymentPolicyService` and `DriftDetectionService`.

#### Missing — Frontend link to Grafana

The issue requires a link to Grafana dashboards from the fact store web UI. No such link exists in any frontend view (`DashboardView.vue`, `NavBar.vue`, etc.).

**Fix required:** Add a "Monitoring" link in `NavBar.vue` pointing to `http://localhost:3000` (or a configurable Grafana URL), and add a metrics panel or link on the dashboard view.

---

## Issue #77 — Documentation Audit and Synchronization

This issue was opened **after** the main implementation work and has not been addressed. The following specific gaps were found during the audit:

### ❌ `DEPLOY.md` — Missing environment variable reference (CRITICAL)

`DEPLOY.md` documents only 4 Spring Boot properties. The following environment variables used in `docker-compose.yml` and `application.yml` are not documented anywhere in `DEPLOY.md`:

| Variable | Used in |
|----------|---------|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `docker-compose.yml` |
| `DB_USERNAME`, `DB_PASSWORD` | `docker-compose.yml` |
| `VAULT_ENABLED`, `VAULT_ADDR`, `VAULT_TOKEN` | `application.yml` |
| `GF_SECURITY_ADMIN_PASSWORD` | `docker-compose.yml` |

**Fix required:** Add a "Configuration Reference" table to `DEPLOY.md` listing all variables, their defaults, and whether they are required.

### ❌ `docs/getting-started/` — Four files incorrectly marked "Coming Soon"

The following files in `docs/getting-started/` contain `🚧 This feature is not yet implemented` banners for features that **are fully implemented**:

| File | Claims not implemented | Reality |
|------|----------------------|---------|
| `03-authentication.md` | Service accounts & API keys | `ServiceAccountController`, `ApiKeyService` exist |
| `08-environments.md` | Environment tracking | `EnvironmentController`, `EnvironmentAllowlistController` exist |
| `09-policies.md` | Policy engine | `PolicyController`, `DeploymentPolicyController` exist |
| `10-approvals.md` | Release approvals | `ApprovalController`, `ApprovalService` with full CRUD exist |

**Fix required:** Either update these files to describe the working implementation, or delete the `docs/getting-started/` directory and update the `README.md` documentation table to point to `USER_GUIDE.md` instead.

### ❌ Spring Boot version mismatch

`README.md` states Spring Boot `3.2.5` but `build.gradle.kts` uses `3.4.13` (upgraded as part of the Gradle 9 migration).

**Fix required:** Update `README.md` tech stack section to `Spring Boot 3.4.13`.

### ❌ No `ROADMAP.md`

The issue asks for aspirational/future features to be moved to a `ROADMAP.md`. None exists. Features that are correctly flagged as future work (CLI tool, mandatory API auth enforcement, SLSA v1.0 attestation, data retention/pruning) have no central home.

**Fix required:** Create `ROADMAP.md` with sections for completed features and planned work.

### Minor — `DEPLOY.md` timing claim

`DEPLOY.md` states setup completes "within two minutes". On first run, Docker image pulls alone take 2–3 minutes. This sets unrealistic expectations.

**Fix required:** Change to "3–5 minutes (includes Docker image pull on first run; subsequent starts are under one minute)".

---

## Prioritised Action List

| Priority | Action | Issue |
|----------|--------|-------|
| 🔴 P1 | Persist `artifactFingerprint` in attestations (add migration + entity field + service update) | #74 |
| 🔴 P1 | Integrate allowlist check into `DeploymentPolicyService.evaluateGate()` | #43 |
| 🔴 P1 | Add env var reference table to `DEPLOY.md` | #77 |
| 🔴 P1 | Fix or delete misleading `docs/getting-started/` "coming soon" files | #77 |
| 🟡 P2 | Add 5 missing Prometheus metrics (gate counters, approvals_pending, drift_detected) | #24 |
| 🟡 P2 | Add Grafana link to frontend NavBar/Dashboard | #24 |
| 🟡 P2 | Validate `org` and `flow` path params in Kosli v2 endpoint | #74 |
| 🟡 P2 | Fix Spring Boot version in `README.md` (3.2.5 → 3.4.13) | #77 |
| 🟡 P2 | Create `ROADMAP.md` | #77 |
| 🟢 P3 | Expand Pact contract tests (failed status, missing fields, fingerprint in response) | #74 |
| 🟢 P3 | Add integration tests for allowlist gate bypass | #43 |
| 🟢 P3 | Add deprecation notice to v1 attestation endpoint | #74 |
| 🟢 P3 | Fix `DEPLOY.md` timing claim ("2 minutes" → "3–5 minutes") | #77 |
