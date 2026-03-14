# OpenFactstore User Guide

A comprehensive guide for developers and DevSecOps engineers using OpenFactstore — a supply chain compliance fact store for financial services and regulated industries.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Setup & Installation](#2-setup--installation)
3. [First Fact — Hello World Tutorial](#3-first-fact--hello-world-tutorial)
4. [Key Features](#4-key-features)
   - [Flow Tags](#41-flow-tags)
   - [Flow Template YAML Specs](#42-flow-template-yaml-specs)
   - [Organisation Multi-tenancy](#43-organisation-multi-tenancy)
   - [Release Approval Workflow](#44-release-approval-workflow)
   - [Deployment Gate & Policy Engine](#45-deployment-gate--policy-engine)
   - [Environment Drift Detection](#46-environment-drift-detection)
   - [Allow-listing Third-party Artifacts](#47-allow-listing-third-party-artifacts)
   - [Snapshotting Scopes](#48-snapshotting-scopes)
   - [Pull Request Attestation](#49-pull-request-attestation)
   - [Security Scan Integration](#410-security-scan-integration)
   - [OPA Policy Integration](#411-opa-policy-integration)
   - [Regulatory Compliance Framework](#412-regulatory-compliance-framework)
   - [Dry-run Safe Mode](#413-dry-run-safe-mode)
   - [CI/CD Integration](#414-cicd-integration)
   - [Prometheus Metrics & Grafana Dashboards](#415-prometheus-metrics--grafana-dashboards)
5. [Security & Data Privacy](#5-security--data-privacy)
6. [CI/CD Integration Reference](#6-cicd-integration-reference)
7. [Monitoring](#7-monitoring)
8. [Lifecycle Management](#8-lifecycle-management)

---

## 1. Introduction

### What is OpenFactstore?

OpenFactstore is an open-source **Supply Chain Compliance Fact Store** — a system of record for proving that your software artifacts (container images, binaries) met all required security and quality gates before reaching production.

It is purpose-built for teams operating in regulated environments — financial services, healthcare, government — where **evidence of compliance** must be collected, timestamped, and queryable on demand for audit, incident response, and regulatory reporting.

At its core, OpenFactstore answers one question at deployment time:

> _"Has this artifact digest satisfied every required compliance check for the flow it belongs to?"_

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Flow** | A named compliance policy defining which attestation types (e.g. `junit`, `snyk`, `trivy`) are required before an artifact may be deployed. Flows belong to an Organisation. |
| **Trail** | A record of one software build, capturing Git metadata (commit SHA, branch, PR number, author). Trails belong to a Flow. |
| **Artifact** | A container image or binary, identified by its SHA-256 digest, associated with a Trail. |
| **Attestation** | Evidence that a requirement was met (test run, scan report, approval decision). Has a status: `PASSED`, `FAILED`, or `PENDING`. |
| **EvidenceFile** | The actual evidence payload (JSON, XML, log file) attached to an Attestation, stored with a cryptographic hash. |
| **Environment** | A named deployment target (e.g. `production`, `staging`). Environments hold snapshots of what artifacts are currently deployed. |
| **Organisation** | A top-level tenant. Flows, users, and integrations are scoped to an Organisation. |

### Architecture Overview

OpenFactstore follows **Hexagonal Architecture** (Ports and Adapters). Business logic in the core domain never depends on infrastructure — only on port interfaces. This means you can swap the database, add new API surfaces, or test business logic in isolation without Spring context.

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Vue 3 SPA)                      │
│             Browser  ─►  Vite Dev Server :5173               │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP / REST (Axios)
┌────────────────────────────▼────────────────────────────────┐
│                 Backend (Spring Boot :8080)                   │
│                                                              │
│  REST Controllers  ─►  Inbound Ports  ─►  Application       │
│                                           Services           │
│                                              │               │
│                                         Outbound Ports       │
│                                              │               │
│                                      JPA Repository          │
│                                         Adapters             │
└────────────────────────────┬────────────────────────────────┘
                             │ JDBC
              ┌──────────────▼──────────────┐
              │      PostgreSQL 16           │
              └─────────────────────────────┘
```

**Tech stack:**
- **Backend:** Kotlin 2.0 · Spring Boot 3.4 · Spring Data JPA · PostgreSQL 16 · Flyway · Java 21
- **Frontend:** Vue 3 (Composition API) · TypeScript 5.4 · Vite 5 · Tailwind CSS 3.4 · Pinia · Axios
- **Ops:** Docker Compose · Prometheus · Grafana · HashiCorp Vault (optional)

---

## 2. Setup & Installation

### Prerequisites

- [ ] **Java 21** (Eclipse Temurin recommended): `java -version`
- [ ] **Node.js 20** + npm: `node -v && npm -v`
- [ ] **Docker & Docker Compose**: `docker compose version`

### Quick Start with Docker Compose

The fastest way to run the full stack:

```bash
git clone https://github.com/MaximumTrainer/OpenFactstore.git
cd OpenFactstore

# Start everything: PostgreSQL, Vault, backend, Prometheus, Grafana
docker compose up --build
```

Services will be available at:

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Frontend | http://localhost:5173 (dev only) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / `changeme`) |
| HashiCorp Vault | http://localhost:8200 |

> 💡 **Pro-tip:** To start only the database (for local backend development), run `docker compose up -d postgres vault`. Then run `./gradlew bootRun` in the `backend/` directory.

### Local Development Setup

**Backend:**

```bash
# 1. Start dependent services
docker compose up -d postgres vault

# 2. Start the Spring Boot dev server
cd backend
./gradlew bootRun
# Listening on http://localhost:8080
```

**Frontend:**

```bash
cd frontend
npm ci
npm run dev
# Listening on http://localhost:5173
```

### Environment Variables

The backend reads these environment variables at startup. All have defaults suitable for local development with Docker Compose.

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `factstore` | Database name |
| `DB_USERNAME` | *(required)* | Database user |
| `DB_PASSWORD` | *(required)* | Database password |
| `VAULT_ENABLED` | `false` | Enable HashiCorp Vault for evidence storage |
| `VAULT_ADDR` | `http://localhost:8200` | Vault address |
| `VAULT_TOKEN` | *(required if enabled)* | Vault root/app token |
| `GF_SECURITY_ADMIN_PASSWORD` | `changeme` | Grafana admin password |

> 💡 **Pro-tip:** For production, set `DB_PASSWORD` and `VAULT_TOKEN` via a secrets manager (AWS Secrets Manager, GCP Secret Manager, Vault itself). Never hardcode credentials.

---

## 3. First Fact — Hello World Tutorial

This tutorial walks you through the complete compliance lifecycle for a single build. You will create a Flow, record a Trail, attach an Attestation, and assert compliance — all with `curl`.

Ensure the backend is running (`http://localhost:8080`).

### Step 1: Create a Flow

A Flow defines the attestation types your artifact must satisfy.

```bash
curl -s -X POST http://localhost:8080/api/v1/flows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-service-compliance",
    "description": "Compliance flow for my-service",
    "requiredAttestations": ["junit", "snyk"]
  }' | jq .
```

**Response:**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "my-service-compliance",
  "description": "Compliance flow for my-service",
  "requiredAttestations": ["junit", "snyk"],
  "createdAt": "2025-01-01T10:00:00Z"
}
```

Save the `id` as `FLOW_ID`:

```bash
export FLOW_ID="3fa85f64-5717-4562-b3fc-2c963f66afa6"
```

### Step 2: Start a Trail

A Trail represents a single build. Attach it to your Flow and record Git metadata.

```bash
curl -s -X POST http://localhost:8080/api/v1/trails \
  -H "Content-Type: application/json" \
  -d "{
    \"flowId\": \"$FLOW_ID\",
    \"gitCommitSha\": \"abc123def456\",
    \"gitBranch\": \"main\",
    \"gitAuthor\": \"alice\",
    \"gitAuthorEmail\": \"alice@example.com\"
  }" | jq .
```

**Response:**
```json
{
  "id": "7b9d1e2f-3a4b-5c6d-7e8f-9a0b1c2d3e4f",
  "flowId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "gitCommitSha": "abc123def456",
  "gitBranch": "main",
  "gitAuthor": "alice",
  "status": "IN_PROGRESS"
}
```

```bash
export TRAIL_ID="7b9d1e2f-3a4b-5c6d-7e8f-9a0b1c2d3e4f"
```

### Step 3: Report an Artifact

Register the container image produced by this build.

```bash
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-service",
    "sha256Digest": "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "tag": "v1.2.3",
    "registry": "ghcr.io/my-org"
  }' | jq .
```

### Step 4: Record Attestations

Record that unit tests passed:

```bash
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "junit",
    "status": "PASSED",
    "description": "All 247 unit tests passed",
    "metadata": {
      "total": 247,
      "passed": 247,
      "failed": 0
    }
  }' | jq .
```

Record that a security scan passed:

```bash
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "snyk",
    "status": "PASSED",
    "description": "No critical vulnerabilities found",
    "metadata": {
      "critical": 0,
      "high": 0,
      "medium": 2
    }
  }' | jq .
```

### Step 5: Assert Compliance

Check whether the artifact satisfies all requirements of the Flow:

```bash
curl -s -X POST http://localhost:8080/api/v1/assert \
  -H "Content-Type: application/json" \
  -d "{
    \"flowId\": \"$FLOW_ID\",
    \"sha256Digest\": \"sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"
  }" | jq .
```

**COMPLIANT response:**
```json
{
  "compliant": true,
  "flowId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sha256Digest": "sha256:e3b0c44...",
  "checkedAt": "2025-01-01T10:05:00Z",
  "attestations": [
    { "type": "junit",  "status": "PASSED" },
    { "type": "snyk",   "status": "PASSED" }
  ]
}
```

If any required attestation is missing or failed, `compliant` will be `false` with a `missingAttestations` list.

> 💡 **Pro-tip:** Wire this `POST /api/v1/assert` call into your deployment pipeline as a **deployment gate**. Block the deploy if `compliant` is `false`.

---

## 4. Key Features

### 4.1 Flow Tags

Flows support arbitrary key-value tags for filtering, reporting, and multi-team organisation.

```bash
# Create a flow with tags
curl -s -X POST http://localhost:8080/api/v1/flows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "payments-service",
    "requiredAttestations": ["junit", "trivy"],
    "tags": {
      "team": "payments",
      "criticality": "high",
      "pci-scope": "true"
    }
  }' | jq .
```

Use tags to group flows for dashboards, notifications, and policy rules. Tags do not affect compliance evaluation.

---

### 4.2 Flow Template YAML Specs

Define flows as YAML templates for version-controlled, reusable compliance specs. Retrieve a flow's YAML template:

```bash
curl -s http://localhost:8080/api/v1/flows/$FLOW_ID/template
```

**Example template output:**
```yaml
name: payments-service
description: Compliance flow for the payments microservice
requiredAttestations:
  - junit
  - snyk
  - trivy
tags:
  team: payments
  criticality: high
```

Store templates in your repository and use them to provision flows programmatically during bootstrap.

---

### 4.3 Organisation Multi-tenancy

OpenFactstore supports multiple tenants via **Organisations**. Each organisation has its own flows, users, and integrations, fully isolated from other organisations.

```bash
# Create an organisation
curl -s -X POST http://localhost:8080/api/v1/organisations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ACME Corp",
    "slug": "acme-corp"
  }' | jq .

# List flows scoped to an organisation
curl -s http://localhost:8080/api/v1/organisations/acme-corp/flows | jq .

# Invite a user to an organisation
curl -s -X POST http://localhost:8080/api/v1/organisations/acme-corp/members \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-uuid",
    "role": "MEMBER"
  }' | jq .
```

**Member roles:** `OWNER`, `ADMIN`, `MEMBER`

---

### 4.4 Release Approval Workflow

For high-risk deployments, require explicit human approval before a trail can be considered compliant.

```bash
# Request approval for a trail
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/approvals" \
  -H "Content-Type: application/json" \
  -d '{
    "requestedBy": "ci-bot",
    "reason": "Release v1.2.3 to production"
  }' | jq .

# Approve (by an authorised reviewer)
export APPROVAL_ID="approval-uuid-here"
curl -s -X POST "http://localhost:8080/api/v1/approvals/$APPROVAL_ID/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "approvedBy": "alice",
    "comment": "LGTM — all checks pass"
  }' | jq .

# Reject
curl -s -X POST "http://localhost:8080/api/v1/approvals/$APPROVAL_ID/reject" \
  -H "Content-Type: application/json" \
  -d '{
    "rejectedBy": "bob",
    "reason": "Pending security review"
  }' | jq .
```

> 💡 **Pro-tip:** Configure Slack notifications (see [4.14](#414-cicd-integration)) so reviewers receive an interactive approval request directly in their Slack workspace.

---

### 4.5 Deployment Gate & Policy Engine

The deployment gate evaluates whether an artifact is cleared to deploy to a specific environment, applying both compliance rules and deployment policies.

```bash
# Create a deployment policy
curl -s -X POST http://localhost:8080/api/v1/deployment-policies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production-gate",
    "flowId": "'$FLOW_ID'",
    "requireApproval": true,
    "blockOnFailedScan": true
  }' | jq .

# Evaluate the deployment gate
curl -s -X POST http://localhost:8080/api/v1/gate/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "artifactDigest": "sha256:e3b0c44...",
    "targetEnvironment": "production",
    "flowId": "'$FLOW_ID'"
  }' | jq .
```

**Gate response:**
```json
{
  "allowed": true,
  "policyId": "production-gate-uuid",
  "reasons": []
}
```

---

### 4.6 Environment Drift Detection

Track what is deployed in each environment, compare snapshots, and detect when environments diverge from their baseline.

```bash
# Register an environment
curl -s -X POST http://localhost:8080/api/v1/environments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production",
    "description": "Production Kubernetes cluster"
  }' | jq .

export ENV_ID="env-uuid"

# Record a snapshot (what is currently deployed)
curl -s -X POST "http://localhost:8080/api/v1/environments/$ENV_ID/snapshots" \
  -H "Content-Type: application/json" \
  -d '{
    "artifacts": [
      {
        "name": "my-service",
        "sha256Digest": "sha256:e3b0c44...",
        "tag": "v1.2.3"
      }
    ]
  }' | jq .

# Set a baseline (the known-good state)
curl -s -X POST "http://localhost:8080/api/v1/environments/$ENV_ID/baselines" \
  -H "Content-Type: application/json" \
  -d '{ "description": "Post-release v1.2.3 baseline" }' | jq .

# Check for drift (after a new snapshot)
curl -s "http://localhost:8080/api/v1/environments/$ENV_ID/drift" | jq .
```

---

### 4.7 Allow-listing Third-party Artifacts

Not all deployed artifacts are built by your own pipelines (e.g. sidecar proxies, operator images). Add them to the environment allow-list to prevent false drift alerts.

```bash
curl -s -X POST "http://localhost:8080/api/v1/environments/$ENV_ID/allowlist" \
  -H "Content-Type: application/json" \
  -d '{
    "artifactName": "envoy-proxy",
    "sha256Digest": "sha256:abc123...",
    "reason": "Managed by platform team — not built by app pipeline"
  }' | jq .
```

---

### 4.8 Snapshotting Scopes

Record scoped snapshots to track compliance state across different scopes (team, service group, namespace):

```bash
curl -s -X POST "http://localhost:8080/api/v1/environments/$ENV_ID/snapshots" \
  -H "Content-Type: application/json" \
  -d '{
    "scope": "payments-team",
    "artifacts": [
      { "name": "payments-api",   "sha256Digest": "sha256:aaa...", "tag": "v2.1.0" },
      { "name": "payments-worker","sha256Digest": "sha256:bbb...", "tag": "v2.1.0" }
    ]
  }' | jq .
```

Diff two snapshots by their index:

```bash
curl -s "http://localhost:8080/api/v1/environments/$ENV_ID/diff?from=1&to=2" | jq .
```

---

### 4.9 Pull Request Attestation

Record an attestation sourced directly from your SCM (GitHub, GitLab, Bitbucket) to prove a PR exists, was reviewed, and was merged by an authorised author.

**Step 1 — Register an SCM integration for your organisation:**

```bash
curl -s -X POST http://localhost:8080/api/v1/organisations/acme-corp/scm-integrations \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "github",
    "baseUrl": "https://api.github.com",
    "token": "ghp_your_token_here"
  }' | jq .
```

> 💡 **Pro-tip:** The token is stored Base64-encoded. For production, enable Vault (`VAULT_ENABLED=true`) to store SCM tokens encrypted at rest.

**Step 2 — Record a PR attestation:**

```bash
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/attestations/pull-request" \
  -H "Content-Type: application/json" \
  -d '{
    "organisationSlug": "acme-corp",
    "provider": "github",
    "repositoryOwner": "my-org",
    "repositoryName": "my-service",
    "prNumber": 42
  }' | jq .
```

OpenFactstore fetches the PR details (title, author, merge status, reviewers) from the SCM and stores the evidence automatically.

---

### 4.10 Security Scan Integration

Record the output of any security scanner (Snyk, Trivy, Grype, Semgrep) as a structured attestation.

```bash
curl -s -X POST "http://localhost:8080/api/v1/trails/$TRAIL_ID/security-scans" \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "trivy",
    "version": "0.50.0",
    "target": "my-service:v1.2.3",
    "summary": {
      "critical": 0,
      "high": 1,
      "medium": 3,
      "low": 12
    },
    "passed": true
  }' | jq .

# Set thresholds that a flow must satisfy
curl -s -X POST "http://localhost:8080/api/v1/flows/$FLOW_ID/security-thresholds" \
  -H "Content-Type: application/json" \
  -d '{
    "maxCritical": 0,
    "maxHigh": 0,
    "maxMedium": 10
  }' | jq .
```

---

### 4.11 OPA Policy Integration

Upload Rego policy bundles and evaluate any artifact's compliance facts against your custom policies using [Open Policy Agent](https://www.openpolicyagent.org/).

```bash
# Upload a Rego policy bundle (ZIP file)
curl -s -X POST http://localhost:8080/api/v1/opa/bundles \
  -F "file=@my-policy-bundle.zip" \
  -F "name=security-baseline-v1" | jq .

export BUNDLE_ID="bundle-uuid"

# Activate the bundle
curl -s -X PUT "http://localhost:8080/api/v1/opa/bundles/$BUNDLE_ID/activate" | jq .

# Evaluate an artifact against the active policy
curl -s -X POST http://localhost:8080/api/v1/opa/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "trailId": "'$TRAIL_ID'",
    "artifactDigest": "sha256:e3b0c44..."
  }' | jq .
```

All policy decisions are logged and queryable via `GET /api/v1/opa/decisions`.

---

### 4.12 Regulatory Compliance Framework

Map your compliance flows to regulatory controls (SOX, PCI-DSS, GDPR, ISO 27001) and generate audit reports automatically.

```bash
# Create a regulatory framework
curl -s -X POST http://localhost:8080/api/v1/frameworks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PCI-DSS v4.0",
    "description": "Payment Card Industry Data Security Standard"
  }' | jq .

export FRAMEWORK_ID="framework-uuid"

# Add controls to the framework
curl -s -X POST "http://localhost:8080/api/v1/frameworks/$FRAMEWORK_ID/controls" \
  -H "Content-Type: application/json" \
  -d '{
    "controlId": "6.3.3",
    "description": "All software components are protected from known vulnerabilities",
    "category": "Secure Software"
  }' | jq .

# Map a compliance flow to a control
curl -s -X POST http://localhost:8080/api/v1/compliance/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "frameworkId": "'$FRAMEWORK_ID'",
    "controlId": "6.3.3",
    "flowId": "'$FLOW_ID'",
    "attestationType": "trivy"
  }' | jq .

# Run a compliance assessment
curl -s -X POST http://localhost:8080/api/v1/compliance/assess \
  -H "Content-Type: application/json" \
  -d '{
    "trailId": "'$TRAIL_ID'",
    "frameworkId": "'$FRAMEWORK_ID'"
  }' | jq .

# Generate a regulatory report
curl -s "http://localhost:8080/api/v1/reports/regulatory/$FRAMEWORK_ID" | jq .
```

---

### 4.13 Dry-run Safe Mode

Use the `X-Dry-Run: true` header on any mutating request to preview what would happen without persisting any data.

```bash
# Simulate an assertion without recording the result
curl -s -X POST http://localhost:8080/api/v1/assert \
  -H "Content-Type: application/json" \
  -H "X-Dry-Run: true" \
  -d '{
    "flowId": "'$FLOW_ID'",
    "sha256Digest": "sha256:e3b0c44..."
  }' | jq .
```

The response includes a `dryRun: true` flag and a human-readable `dryRunMessage`. No side-effects are committed.

> 💡 **Pro-tip:** Use dry-run in pull request CI checks to validate that the artifact *would* be compliant without actually recording a compliance result. This gives early feedback without polluting the audit trail.

---

### 4.14 CI/CD Integration

See the full guide at **[docs/ci-integration.md](./docs/ci-integration.md)**.

**Quick example — GitHub Actions:**

```yaml
- name: Create Factstore trail
  run: |
    curl -s -X POST "${{ vars.FACTSTORE_BASE_URL }}/api/v1/trails" \
      -H "Content-Type: application/json" \
      -H "X-Factstore-CI-Context: github-actions" \
      -d '{
        "flowId": "${{ vars.FACTSTORE_FLOW_ID }}",
        "gitAuthor": "${{ github.actor }}",
        "gitAuthorEmail": "${{ github.actor }}@users.noreply.github.com"
      }'

- name: Assert compliance before deploy
  run: |
    RESULT=$(curl -s -X POST "${{ vars.FACTSTORE_BASE_URL }}/api/v1/assert" \
      -H "Content-Type: application/json" \
      -d '{"flowId":"${{ vars.FACTSTORE_FLOW_ID }}","sha256Digest":"'"$DIGEST"'"}')
    COMPLIANT=$(echo $RESULT | jq -r '.compliant')
    if [ "$COMPLIANT" != "true" ]; then
      echo "Deployment blocked: artifact is not compliant"
      exit 1
    fi
```

The `X-Factstore-CI-Context` header instructs the server to auto-populate `gitCommitSha`, `gitBranch`, and `buildUrl` from the CI environment's standard variables. See [docs/ci-integration.md](./docs/ci-integration.md) for all supported CI systems (GitHub Actions, GitLab CI, Jenkins, CircleCI, Azure DevOps).

---

### 4.15 Prometheus Metrics & Grafana Dashboards

OpenFactstore exposes Prometheus metrics at `/actuator/prometheus`. See [Section 7: Monitoring](#7-monitoring) for the full guide.

---

## 5. Security & Data Privacy

### API Authentication

> ⚠️ **Current state:** The server currently trusts all requests without authentication. API key enforcement is implemented (see `POST /api/v1/api-keys`), but is not required by default in the current version. A future release will make authentication mandatory.

**For production deployments:**

1. Create service accounts and API keys for each CI system:

```bash
# Create a service account for your CI pipeline
curl -s -X POST http://localhost:8080/api/v1/service-accounts \
  -H "Content-Type: application/json" \
  -d '{ "name": "github-actions-ci", "description": "GitHub Actions pipeline" }' | jq .

# Generate an API key — returned once, store securely
curl -s -X POST "http://localhost:8080/api/v1/service-accounts/$SA_ID/api-keys" \
  -H "Content-Type: application/json" \
  -d '{ "name": "prod-pipeline-key" }' | jq .
```

2. Pass the key in the `X-Api-Key` header on all requests.
3. Rotate keys periodically using the revoke + regenerate flow.

### Data at Rest

| Storage | Dev default | Production recommendation |
|---------|-------------|---------------------------|
| Relational data | H2 in-memory (unit tests) / PostgreSQL (dev/prod) | PostgreSQL 16 with encrypted volumes (e.g. AWS RDS with encryption-at-rest enabled) |
| Evidence files | Local DB (Base64) | HashiCorp Vault (`VAULT_ENABLED=true`) |
| SCM tokens | Base64-encoded in PostgreSQL | HashiCorp Vault for token storage + KMS-backed encryption |

> 💡 **Pro-tip:** Enable `VAULT_ENABLED=true` in production to store all evidence files and SCM tokens in HashiCorp Vault. The `vault` service is included in the Docker Compose file for local testing.

### SCM Token Storage

When you register an SCM integration (GitHub, GitLab), the token is stored Base64-encoded. This provides encoding, not encryption. **For production:**

1. Set `VAULT_ENABLED=true` and configure the Vault address and token.
2. The application will automatically use Vault as the evidence and secret storage backend.
3. Consider a KMS-backed Vault auto-unseal for fully automated operations.

### Best Practices

- Rotate API keys every 90 days.
- Use separate service accounts per CI pipeline/environment.
- Restrict network access to the Factstore API to internal networks or VPN.
- Enable TLS on all service-to-service communication.
- Review the Grafana dashboards regularly to detect anomalous compliance patterns.
- Store `DB_PASSWORD`, `VAULT_TOKEN`, and SCM tokens in a secrets manager, not in environment files committed to source control.

---

## 6. CI/CD Integration Reference

See **[docs/ci-integration.md](./docs/ci-integration.md)** for the complete guide, including examples for:

- **GitHub Actions** (with `X-Factstore-CI-Context: github-actions`)
- **GitLab CI/CD** (with `X-Factstore-CI-Context: gitlab-ci`)
- **Jenkins** (with `X-Factstore-CI-Context: jenkins`)
- **CircleCI** (with `X-Factstore-CI-Context: circleci`)
- **Azure DevOps** (with `X-Factstore-CI-Context: azure-devops`)

The `X-Factstore-CI-Context` header auto-populates `gitCommitSha`, `gitBranch`, and `buildUrl` from well-known CI environment variables. Explicitly provided fields in the request body always take precedence.

---

## 7. Monitoring

### Prometheus Endpoint

```
GET /actuator/prometheus
```

OpenFactstore's Spring Boot application exposes metrics via the Micrometer → Prometheus bridge.

**Custom metrics:**

| Metric | Type | Description |
|--------|------|-------------|
| `factstore_flows_total` | Counter | Total number of flows created |
| `factstore_trails_total` | Counter | Total number of trails created |
| `factstore_attestations_total` | Counter | Attestations recorded, labelled by `type` and `status` |
| `factstore_assert_requests_total` | Counter | Compliance assertions performed |
| `factstore_assert_compliant_total` | Counter | Assertions that returned compliant |
| `factstore_assert_noncompliant_total` | Counter | Assertions that returned non-compliant |
| `factstore_security_scans_total` | Counter | Security scans recorded, labelled by `tool` |
| `factstore_gate_evaluations_total` | Counter | Deployment gate evaluations |

**Standard Spring Boot metrics** (HTTP, JVM, HikariCP, etc.) are also exposed.

### Prometheus Configuration

The `monitoring/prometheus.yml` file is pre-configured to scrape the backend at `backend:8080`. When running with Docker Compose, Prometheus is automatically configured.

### Grafana Dashboards

The `monitoring/dashboards/` directory contains four pre-built dashboards:

| Dashboard | File | Description |
|-----------|------|-------------|
| Compliance Overview | `compliance-overview.json` | Flow compliance rates, attestation trends |
| Security Scans | `security-scans.json` | Vulnerability trends by tool and severity |
| Deployment Gates | `deployment-gates.json` | Gate pass/block rates by environment |
| Audit & Forensics | `audit-forensics.json` | Audit event volume and anomaly detection |

Dashboards are auto-provisioned via `monitoring/provisioning/`. Open http://localhost:3000 and log in as `admin` / `changeme` (or the value of `GF_SECURITY_ADMIN_PASSWORD`).

---

## 8. Lifecycle Management

### Fact Versioning via Trails

Each build creates a new Trail. Trails are immutable once created — you can only add attestations, not modify existing ones. This creates an append-only, tamper-evident compliance history.

To see the history of compliance for an artifact, search by its SHA-256 digest:

```bash
curl -s "http://localhost:8080/api/v1/artifacts?sha256=sha256:e3b0c44..." | jq .
```

To get the full chain of custody for an artifact:

```bash
curl -s "http://localhost:8080/api/v1/compliance/artifact/sha256:e3b0c44..." | jq .
```

### Trail Status Lifecycle

Trails move through the following states:

```
IN_PROGRESS → COMPLIANT
            → NON_COMPLIANT
```

| Status | Meaning |
|--------|---------|
| `IN_PROGRESS` | Trail has been created but not all required attestations have been recorded. |
| `COMPLIANT` | All required attestations are present and passed. The associated artifact is safe to deploy. |
| `NON_COMPLIANT` | One or more required attestations are missing or failed. |

### Audit Trail

Every mutating action is recorded in the immutable audit log:

```bash
# Query audit events
curl -s "http://localhost:8080/api/v1/audit?trailId=$TRAIL_ID&sortDesc=true" | jq .

# Export full audit trail for a trail
curl -s "http://localhost:8080/api/v1/reports/audit-trail/$TRAIL_ID" | jq .
```

### Data Retention

The current version does not implement automatic data pruning. For production:

- PostgreSQL's native partitioning by `createdAt` month enables efficient pruning of old trails.
- Archive trails older than your regulatory retention window (typically 7 years for SOX/PCI) to cold storage before deleting.
- Evidence files in Vault can be soft-deleted via `DELETE /api/v1/evidence/{entityType}/{entityId}` and later purged.

---

*For API reference, see [docs/API_REFERENCE.md](./docs/API_REFERENCE.md). For deployment, see [DEPLOY.md](./DEPLOY.md). For security vulnerability reporting, see [SECURITY.md](./SECURITY.md).*
