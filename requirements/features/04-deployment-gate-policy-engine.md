---
title: "Feature: Deployment Gate & Policy Engine"
labels: ["enhancement", "kosli-feature", "policy", "deployment-gate"]
---

## Summary

Implement a policy engine that defines deployment gates: before an artifact is allowed into an environment, it must satisfy a configurable set of policies. This mirrors Kosli's _Deployment Controls_ feature: _"automatically ensure only compliant software is deployed by verifying binary provenance, risk controls, and approvals"_.

## Motivation

Factstore's current `POST /api/v1/assert` endpoint can check compliance but does not actively *prevent* deployment. A deployment gate is an enforcement point — a hard stop that prevents non-compliant artifacts from reaching production. This is the key difference between *visibility* and *enforcement*.

## Requirements

### Data Model

- **DeploymentPolicy** entity:
  - `id`, `name`, `description`, `environmentId` (optional — policies can be environment-scoped)
  - `flowId` — which flow's requirements must be met
  - `enforceProvenance` boolean — require artifact build provenance
  - `enforceApprovals` boolean — require all approvals to be granted
  - `requiredAttestationTypes` — list of attestation types that must PASS
  - `isActive` boolean
  - `createdAt`, `updatedAt`

- **DeploymentGateResult** entity:
  - `id`, `policyId`, `artifactSha256`, `environmentId`
  - `decision`: `ALLOWED | BLOCKED`
  - `evaluatedAt`
  - `blockReasons`: list of reason strings (e.g., "Missing attestation: snyk", "Approval PENDING")

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/policies` | Create a deployment policy |
| GET | `/api/v1/policies` | List all policies |
| GET | `/api/v1/policies/{id}` | Get policy details |
| PUT | `/api/v1/policies/{id}` | Update policy |
| DELETE | `/api/v1/policies/{id}` | Delete policy |
| POST | `/api/v1/gate/evaluate` | Evaluate an artifact against policies for an environment |
| GET | `/api/v1/gate/results` | List gate evaluation results |

### Gate Evaluation Request

```json
{
  "artifactSha256": "sha256:abc123...",
  "environmentId": "uuid-of-production",
  "requestedBy": "deployment-pipeline@ci"
}
```

### Gate Evaluation Response

```json
{
  "decision": "BLOCKED",
  "artifactSha256": "sha256:abc123...",
  "environmentId": "uuid-of-production",
  "evaluatedAt": "2025-01-01T12:00:00Z",
  "blockReasons": [
    "Missing attestation: snyk",
    "Approval status: PENDING_APPROVAL"
  ]
}
```

### Frontend

- New **Policies** page for managing deployment policies
- Gate evaluation result history per environment
- Visual "blocked" indicator on Trail Detail when gate would block deployment
- Dashboard widget showing recent gate decisions

## Acceptance Criteria

- [ ] DeploymentPolicy and DeploymentGateResult entities created
- [ ] Policy CRUD API implemented
- [ ] Gate evaluation endpoint implemented and tested
- [ ] Gate blocks deployments that fail provenance, attestation, or approval checks
- [ ] Gate evaluation results are persisted for audit
- [ ] Unit tests cover policy engine evaluation logic
- [ ] Frontend Policies page implemented
- [ ] OpenAPI documentation is updated
