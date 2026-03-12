---
title: "Feature: Runtime Forensics & Immutable Audit Log"
labels: ["enhancement", "kosli-feature", "forensics", "audit-log"]
---

## Summary

Implement a fully immutable, append-only audit log that records every change event in environments — what was deployed, updated, or removed, and when and by whom. This mirrors Kosli's _Runtime Forensics_ feature: _"record every change to every environment in a fully auditable environment log"_.

## Motivation

Factstore captures compliance attestations but does not record what actually happened at runtime. In a security incident or compliance audit, investigators need to answer: _"What exactly changed in production at 14:32 on Tuesday?"_ An immutable audit log makes this possible.

## Requirements

### Data Model

- **AuditEvent** entity (append-only, never updated or deleted):
  - `id` (UUID)
  - `eventType`: `ARTIFACT_DEPLOYED | ARTIFACT_REMOVED | ARTIFACT_UPDATED | ENVIRONMENT_CREATED | ENVIRONMENT_DELETED | POLICY_EVALUATED | ATTESTATION_RECORDED | APPROVAL_GRANTED | APPROVAL_REJECTED | GATE_BLOCKED | GATE_ALLOWED`
  - `environmentId` (nullable — for environment-scoped events)
  - `trailId` (nullable)
  - `artifactSha256` (nullable)
  - `actor` — identity of the initiator (user, service account, pipeline)
  - `payload` — JSON blob with event-specific details
  - `occurredAt` — immutable timestamp

### API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/audit` | Query audit events (filterable by type, environment, actor, date range) |
| GET | `/api/v1/audit/{id}` | Get a specific audit event |
| GET | `/api/v1/environments/{id}/audit` | Get audit events for a specific environment |
| GET | `/api/v1/trails/{id}/audit` | Get audit events for a specific trail |

### Immutability Requirements

- No UPDATE or DELETE operations are permitted on audit events
- All write paths (deployments, attestations, approvals, gate evaluations) MUST emit an audit event
- Audit events are written synchronously before the primary operation completes
- Consider append-only table constraints at the database level

### Query & Filtering

- Filter by `eventType`, `environmentId`, `actor`, date range (`from`, `to`)
- Pagination support (cursor-based for large result sets)
- Full-text search on `payload` field
- Sort by `occurredAt` ascending or descending

### Frontend

- New **Audit Log** page with filterable event stream
- Environment-scoped audit log tab on Environment detail page
- Trail-scoped audit log tab on Trail detail page

## Acceptance Criteria

- [ ] AuditEvent entity created with no update/delete operations permitted
- [ ] All write paths emit audit events (tested via integration tests)
- [ ] Audit query API with full filtering and pagination implemented
- [ ] AuditEvent is written for: deployments, attestations, approvals, gate evaluations
- [ ] Unit tests cover audit event emission from all services
- [ ] Frontend Audit Log page implemented
- [ ] OpenAPI documentation is updated
