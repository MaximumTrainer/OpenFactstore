---
title: "Feature: Enhanced Query & Reporting API"
labels: ["enhancement", "kosli-feature", "reporting", "query"]
---

## Summary

Implement advanced query capabilities and compliance report generation. Operators need to answer complex questions across time, environments, and flows. This implements Kosli's _Query Anything_ feature: _"Kosli can tell you what's running in any environment now, in the past, and how it has changed"_ — and Kosli's _4D Observability_: _"tell you what's in production, how it got there, and if it's compliant — for any point in time"_.

## Motivation

Factstore's current query capabilities are basic: list endpoints with simple filtering. Real-world compliance and audit scenarios require time-travel queries, cross-entity searches, and exportable reports. Regulated industries require periodic compliance reports that can be submitted to auditors.

## Requirements

### Time-Travel Queries

- Query what was running in an environment at a specific timestamp: `GET /api/v1/environments/{id}/state?at=2025-01-01T12:00:00Z`
- Query the compliance status of an artifact at a specific point in time
- Query which flows were active and their compliance state on a given date

### Cross-Entity Search

- Full-text search across trails, artifacts, and attestations
- Search by git commit author, branch, or commit SHA prefix
- Search artifacts by name prefix or tag pattern
- `GET /api/v1/search?q=...&type=trail|artifact|attestation`

### Compliance Summary Reports

- Per-flow compliance summary: total trails, compliant %, non-compliant trails list
- Per-environment compliance summary: current state vs baseline, drift status
- Date-range compliance report: compliance rate over time (for trend analysis)

### Report Export

- Export compliance reports as JSON (structured, machine-readable)
- Export audit trails as JSON for submission to external audit systems
- `GET /api/v1/reports/compliance?flowId=...&from=...&to=...&format=json`

### Advanced Filtering & Pagination

- All list endpoints to support:
  - Cursor-based pagination (`after`, `before` cursor params)
  - Field-level filtering (`?status=COMPLIANT&gitBranch=main`)
  - Sorting (`?sort=createdAt&order=desc`)
  - Field projection (`?fields=id,name,status`)

### API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/environments/{id}/state` | Point-in-time environment state query |
| GET | `/api/v1/search` | Cross-entity full-text search |
| GET | `/api/v1/reports/compliance` | Compliance summary report |
| GET | `/api/v1/reports/audit-trail/{trailId}` | Full audit trail export for one trail |
| GET | `/api/v1/dashboard/stats` | Aggregate dashboard statistics |

### Frontend

- Advanced search bar with filters on Flows, Trails, and Artifacts pages
- Compliance trend chart on Dashboard (compliance rate over time)
- Report export button on Flow Detail and Environment Detail pages

## Acceptance Criteria

- [ ] Point-in-time environment state query implemented
- [ ] Full-text search across trails and artifacts implemented
- [ ] Per-flow compliance summary report endpoint implemented
- [ ] JSON report export implemented
- [ ] Cursor-based pagination added to all list endpoints
- [ ] Field-level filtering added to Trail and Artifact list endpoints
- [ ] Unit tests cover query logic and report generation
- [ ] Frontend advanced search and compliance chart implemented
- [ ] OpenAPI documentation is updated
