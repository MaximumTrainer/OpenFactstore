---
title: "Feature: Notifications & Compliance Alerts"
labels: ["enhancement", "kosli-feature", "notifications", "alerting"]
---

## Summary

Implement a configurable notification system that alerts operators in real time when compliance violations occur, deployments are blocked, drift is detected, or approvals are required. Supports outbound webhooks (Slack, generic HTTP), with a pluggable architecture for future channels (email, PagerDuty).

## Motivation

Without notifications, operators must actively poll factstore to detect problems. In a production environment, compliance violations (a failed security scan, a blocked deployment, a drift event) need to be surfaced immediately to the right people. _Kosli's value is not just recording what happened — it's making sure the right people know about it instantly._

## Requirements

### Notification Channels

- **SlackChannel**: sends formatted messages to a Slack webhook URL
- **WebhookChannel**: sends a JSON payload to any HTTP(S) endpoint
- **InAppChannel**: persists notifications in the database for the web UI notification centre

### Notification Rule Engine

- **NotificationRule** entity:
  - `id`, `name`, `isActive`
  - `triggerEvent`: one of `ATTESTATION_FAILED | GATE_BLOCKED | DRIFT_DETECTED | APPROVAL_REQUIRED | TRAIL_NON_COMPLIANT | APPROVAL_REJECTED`
  - `channelType`: `SLACK | WEBHOOK | IN_APP`
  - `channelConfig`: JSON blob (e.g., `{"webhookUrl": "https://hooks.slack.com/..."}`)
  - `filterFlowId` (optional — only fire for specific flows)
  - `filterEnvironmentId` (optional — only fire for specific environments)
  - `createdAt`, `updatedAt`

### Notification Delivery

- **NotificationDelivery** entity (audit log of sent notifications):
  - `id`, `ruleId`, `eventType`, `payload`, `status` (`SENT | FAILED | SKIPPED`), `sentAt`, `error`

- Notification delivery is asynchronous (Spring `@Async` or `ApplicationEventPublisher`)
- Retry on failure: up to 3 attempts with exponential backoff
- Dead-letter log for permanently failed deliveries

### In-App Notification Centre

- **Notification** entity: `id`, `title`, `message`, `severity` (`INFO | WARNING | CRITICAL`), `isRead`, `entityType`, `entityId`, `createdAt`
- `GET /api/v1/notifications` — list in-app notifications (filter by `isRead`, `severity`)
- `POST /api/v1/notifications/{id}/read` — mark as read
- `POST /api/v1/notifications/read-all` — mark all as read

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/notification-rules` | Create a notification rule |
| GET | `/api/v1/notification-rules` | List all notification rules |
| PUT | `/api/v1/notification-rules/{id}` | Update a rule |
| DELETE | `/api/v1/notification-rules/{id}` | Delete a rule |
| POST | `/api/v1/notification-rules/{id}/test` | Send a test notification |
| GET | `/api/v1/notification-rules/{id}/deliveries` | View delivery history |
| GET | `/api/v1/notifications` | List in-app notifications |
| POST | `/api/v1/notifications/{id}/read` | Mark in-app notification as read |

### Slack Message Format

```
🚨 *Compliance Alert* — Factstore
*Type*: Gate Blocked
*Artifact*: `sha256:abc123...`
*Environment*: production
*Reason*: Missing attestation: snyk-scan
*Trail*: <link>
*Time*: 2025-01-01T12:00:00Z
```

### Frontend

- Notification bell icon in NavBar with unread count badge
- Notification dropdown/panel listing recent in-app notifications
- New **Notification Rules** settings page for managing rules and channels
- Delivery history per rule

## Acceptance Criteria

- [ ] NotificationRule and NotificationDelivery entities created
- [ ] Slack webhook channel implemented and tested (with mock Slack server in tests)
- [ ] Generic HTTP webhook channel implemented
- [ ] In-app notification centre implemented (entity + API + frontend bell)
- [ ] Async delivery with retry logic (3 attempts, exponential backoff)
- [ ] Events published from: AttestationService (fail), GateService (block), DriftService (drift), ApprovalService (required/rejected)
- [ ] Notification Rules CRUD API implemented
- [ ] Test notification endpoint implemented
- [ ] Unit tests cover rule evaluation and channel dispatch logic
- [ ] Frontend notification bell and settings page implemented
- [ ] OpenAPI documentation is updated
