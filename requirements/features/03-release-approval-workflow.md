---
title: "Feature: Release Approval Workflow"
labels: ["enhancement", "kosli-feature", "approvals", "governance"]
---

## Summary

Implement a formalized, multi-party release approval workflow. Operators can define who must approve a release, and releases cannot proceed until all required approvals are granted. This mirrors Kosli's _Release Approvals_ feature: _"generate release approvals via version control, CI, or even Slack events"_.

## Motivation

Factstore currently has no approval mechanism. In regulated industries, a release to production typically requires sign-off from multiple parties (e.g., security team, QA lead, change advisory board). Without a structured approval workflow, these approvals happen outside the system and cannot be audited.

## Requirements

### Data Model

- **Approval** entity:
  - `id`, `trailId`, `flowId`
  - `status`: `PENDING_APPROVAL | APPROVED | REJECTED | EXPIRED`
  - `requiredApprovers`: list of required approver role/identity strings
  - `approvedBy`: list of ActualApprover records
  - `rejectedBy`: ActualApprover record (if rejected)
  - `requestedAt`, `deadline` (optional), `resolvedAt`
  - `comments`: free-text comment from approvers

- **ApprovalDecision** entity:
  - `id`, `approvalId`, `approverIdentity`, `decision` (`APPROVED | REJECTED`), `comments`, `decidedAt`

- **Flow** extension: add `requiresApproval` boolean and `requiredApproverRoles` list

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/trails/{trailId}/approvals` | Request a release approval |
| GET | `/api/v1/trails/{trailId}/approvals` | List approvals for a trail |
| GET | `/api/v1/approvals/{approvalId}` | Get approval details |
| POST | `/api/v1/approvals/{approvalId}/approve` | Approve a release |
| POST | `/api/v1/approvals/{approvalId}/reject` | Reject a release |
| GET | `/api/v1/approvals?status=PENDING_APPROVAL` | List all pending approvals |

### Compliance Integration

- A trail's compliance status must factor in approval status when a flow requires approval
- A trail cannot be `COMPLIANT` if it has a `REJECTED` or `PENDING_APPROVAL` approval (when approval is required)
- The `AssertService` must query for approvals and include them in compliance evaluation

### Frontend

- New **Approvals** page listing all pending approvals
- Approve/Reject action buttons on Trail Detail view
- Approval history timeline on Trail Detail view
- Dashboard widget showing pending approvals count

## Acceptance Criteria

- [ ] Approval and ApprovalDecision entities created
- [ ] Full CRUD API for approval workflow
- [ ] Approve and reject endpoints work correctly
- [ ] Compliance assertion incorporates approval status
- [ ] Flow model extended with approval requirements
- [ ] Unit tests cover approval service and compliance integration
- [ ] Frontend Approvals page is implemented
- [ ] OpenAPI documentation is updated
