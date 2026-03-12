---
title: "Feature: Environment Diff & Drift Detection"
labels: ["enhancement", "kosli-feature", "drift-detection", "environment"]
---

## Summary

Implement the ability to compare two environment snapshots (or a snapshot against an approved baseline) to detect unauthorized or unexpected changes (drift). Alert operators when the running state of an environment diverges from its approved state. This aligns with Kosli's _Environment Policies_ feature: _"real-time compliance evaluations on every change to production ensures you can prove that nothing has circumvented your controls"_.

## Motivation

After an environment snapshot is recorded, operators need to know: _"Has anything changed that shouldn't have?"_ Drift can indicate unauthorized deployments, security incidents, or configuration mistakes. Without automated drift detection, these changes may go unnoticed until an audit.

## Requirements

### Diff Capability

- Compare any two snapshots of an environment (by snapshot index or timestamp)
- Diff result identifies:
  - **Added** artifacts (present in snapshot B but not A)
  - **Removed** artifacts (present in snapshot A but not B)
  - **Updated** artifacts (same name but different SHA256 digest)
  - **Unchanged** artifacts

### Baseline Management

- **EnvironmentBaseline** entity: a designated snapshot (or explicit list of approved artifacts) that represents the "approved" state of an environment
- `id`, `environmentId`, `snapshotId` (nullable — can be based on a snapshot), `approvedBy`, `approvedAt`, `description`
- Only one active baseline per environment at a time

### Drift Detection

- **DriftReport** entity: result of comparing current running state against the active baseline
  - `id`, `environmentId`, `baselineId`, `snapshotId`, `generatedAt`
  - `hasDrift` boolean
  - `addedArtifacts`, `removedArtifacts`, `updatedArtifacts` (lists)

### API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/environments/{id}/diff?from={snapshotIndex}&to={snapshotIndex}` | Diff two snapshots |
| POST | `/api/v1/environments/{id}/baselines` | Set a new approved baseline |
| GET | `/api/v1/environments/{id}/baselines/current` | Get the current baseline |
| GET | `/api/v1/environments/{id}/drift` | Check current drift against baseline |
| GET | `/api/v1/environments/{id}/drift/history` | Drift report history |

### Drift Policies

- Environments can have a `driftPolicy`: `WARN | BLOCK | IGNORE`
- `BLOCK`: new snapshots that show drift mark the environment as `NON_COMPLIANT`
- `WARN`: drift is flagged but environment remains `COMPLIANT`
- Drift status feeds into the overall compliance picture

### Frontend

- Diff view showing side-by-side snapshot comparison
- Drift badge on Environment card (drift detected / clean)
- Baseline management UI on Environment detail page
- Notification dot when drift is detected

## Acceptance Criteria

- [ ] Snapshot diff algorithm implemented and tested
- [ ] Baseline management API and entity implemented
- [ ] Drift detection logic compares current snapshot against baseline
- [ ] DriftReport persisted and queryable
- [ ] Drift policy (WARN/BLOCK) affects environment compliance status
- [ ] Unit tests cover diff and drift detection logic
- [ ] Frontend diff view implemented
- [ ] OpenAPI documentation is updated
