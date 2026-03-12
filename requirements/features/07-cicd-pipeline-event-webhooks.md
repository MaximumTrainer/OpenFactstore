---
title: "Feature: CI/CD Pipeline Event Webhooks"
labels: ["enhancement", "kosli-feature", "webhooks", "integrations"]
---

## Summary

Implement an inbound webhook API that allows external CI/CD systems (GitHub Actions, Jenkins, CircleCI, GitLab CI) to report pipeline events directly to factstore. Events are normalized and routed to the appropriate flows and trails. This mirrors Kosli's _Record Pipeline Events_ feature: _"report every CI pipeline event of interest (builds, security scans, test results, approvals, deployments, etc.)"_.

## Motivation

Currently, factstore's attestation API requires callers to know the `trailId` upfront, and supports only a single attestation format. Real CI/CD pipelines fire many different event types from different systems. An inbound webhook normalizes these events into factstore's data model automatically, lowering integration friction.

## Requirements

### Inbound Webhook API

- `POST /api/v1/webhooks/{source}` where `source` is `github | jenkins | circleci | gitlab | generic`
- Each source has a dedicated parser that maps incoming payloads to factstore entities
- Webhook signature verification (HMAC-SHA256) for each source
- Idempotency: duplicate webhooks (same delivery ID) are deduplicated

### Event Types Supported

| Event Type | Action |
|---|---|
| `build.started` | Create or update trail `status = IN_PROGRESS` |
| `build.succeeded` | Record `BUILD` attestation with status `PASSED` |
| `build.failed` | Record `BUILD` attestation with status `FAILED` |
| `test.passed` | Record `JUNIT` or `TEST` attestation with status `PASSED` |
| `test.failed` | Record `TEST` attestation with status `FAILED` |
| `scan.passed` | Record `SNYK` / `SECURITY_SCAN` attestation with status `PASSED` |
| `scan.failed` | Record `SECURITY_SCAN` attestation with status `FAILED` |
| `deployment.triggered` | Record deployment event on trail |
| `approval.granted` | Record approval on approval workflow |

### Webhook Source Mappings

- **GitHub Actions**: Map `workflow_run` events, extract commit SHA, branch, actor
- **Jenkins**: Map build result events to trails by job name + build number
- **Generic**: Accept a normalized JSON schema that maps directly to factstore events

### Webhook Registration

- **WebhookConfig** entity: `id`, `source`, `secret` (hashed), `flowId`, `isActive`, `createdAt`
- API to register, list, update, and delete webhook configurations
- Webhook delivery log for debugging

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/webhooks/{source}` | Receive incoming webhook from CI/CD system |
| POST | `/api/v1/webhook-configs` | Register a new webhook configuration |
| GET | `/api/v1/webhook-configs` | List webhook configurations |
| DELETE | `/api/v1/webhook-configs/{id}` | Delete a webhook configuration |
| GET | `/api/v1/webhook-configs/{id}/deliveries` | List recent webhook deliveries |

### Frontend

- New **Integrations** page showing registered webhook configurations
- Webhook delivery log with status (success/failure) per delivery
- One-click test webhook button

## Acceptance Criteria

- [ ] Inbound webhook endpoint implemented for `generic` source type
- [ ] GitHub Actions webhook parser implemented
- [ ] HMAC-SHA256 signature verification implemented
- [ ] Webhook deduplication by delivery ID implemented
- [ ] WebhookConfig entity and CRUD API implemented
- [ ] Webhook delivery logging implemented
- [ ] Unit tests cover event parsing and routing for all event types
- [ ] Frontend Integrations page implemented
- [ ] OpenAPI documentation is updated
