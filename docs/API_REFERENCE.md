# API Reference

Complete HTTP API reference for OpenFactstore. All endpoints are under the base path `/api/v1` (or `/api/v2` for v2 endpoints).

Interactive documentation is also available at **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)** when the server is running.

---

## Authentication

> ⚠️ **Current version:** The server accepts all requests without requiring authentication. API key infrastructure is implemented but not yet enforced. For production, place the API behind a proxy that validates the `X-Api-Key` header.

---

## Common Headers

| Header | Description |
|--------|-------------|
| `Content-Type: application/json` | Required on all POST/PUT requests with a JSON body |
| `X-Api-Key: <key>` | API key for service accounts (use in production) |
| `X-Dry-Run: true` | Preview the result of any mutating request without persisting data |
| `X-Factstore-CI-Context: <system>` | On `POST /api/v1/trails` — auto-populate Git fields from CI environment variables. Values: `github-actions`, `gitlab-ci`, `jenkins`, `circleci`, `azure-devops` |

---

## Error Codes

| HTTP Status | Meaning |
|-------------|---------|
| `200 OK` | Request succeeded |
| `201 Created` | Resource created |
| `400 Bad Request` | Invalid request body or missing required field |
| `404 Not Found` | Requested resource does not exist |
| `409 Conflict` | Resource already exists (e.g. duplicate flow name) |
| `422 Unprocessable Entity` | Business rule violation |
| `500 Internal Server Error` | Unexpected server error |

---

## Endpoints by Resource

### Flows

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/flows` | Create a new flow |
| `GET` | `/api/v1/flows` | List all flows |
| `GET` | `/api/v1/flows/{id}` | Get flow by ID |
| `PUT` | `/api/v1/flows/{id}` | Update a flow |
| `DELETE` | `/api/v1/flows/{id}` | Delete a flow |
| `GET` | `/api/v1/flows/{id}/template` | Get flow template as YAML |
| `POST` | `/api/v1/flows/{flowId}/security-thresholds` | Set security scan thresholds for a flow |
| `GET` | `/api/v1/flows/{flowId}/security-thresholds` | Get security scan thresholds for a flow |

**Create flow — request body:**
```json
{
  "name": "my-service-compliance",
  "description": "Optional description",
  "requiredAttestations": ["junit", "snyk", "trivy"],
  "tags": { "team": "payments", "criticality": "high" }
}
```

---

### Trails

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails` | Create a trail (supports `X-Factstore-CI-Context` header) |
| `GET` | `/api/v1/trails` | List trails (optional query param: `flowId`) |
| `GET` | `/api/v1/trails/{id}` | Get trail by ID |
| `GET` | `/api/v1/flows/{flowId}/trails` | List trails for a specific flow |
| `GET` | `/api/v1/trails/{id}/audit` | Get audit events for a trail |

**Create trail — request body:**
```json
{
  "flowId": "uuid",
  "gitCommitSha": "abc123",
  "gitBranch": "main",
  "gitAuthor": "alice",
  "gitAuthorEmail": "alice@example.com",
  "pullRequestNumber": 42,
  "buildUrl": "https://ci.example.com/builds/123"
}
```

---

### Artifacts

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails/{trailId}/artifacts` | Report an artifact for a trail |
| `GET` | `/api/v1/trails/{trailId}/artifacts` | List artifacts for a trail |
| `GET` | `/api/v1/artifacts` | Find artifacts by digest (query param: `sha256`) |
| `POST` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | Record build provenance |
| `GET` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | Get build provenance |
| `GET` | `/api/v1/artifacts/{sha256}/provenance` | Get provenance by SHA-256 digest |
| `POST` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance/verify` | Verify provenance signature |

**Report artifact — request body:**
```json
{
  "name": "my-service",
  "sha256Digest": "sha256:e3b0c44...",
  "tag": "v1.2.3",
  "registry": "ghcr.io/my-org"
}
```

---

### Attestations

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails/{trailId}/attestations` | Record an attestation |
| `GET` | `/api/v1/trails/{trailId}/attestations` | List attestations for a trail |
| `POST` | `/api/v1/trails/{trailId}/attestations/{id}/evidence` | Upload evidence file (multipart/form-data) |
| `POST` | `/api/v1/trails/{trailId}/attestations/pull-request` | Record a PR attestation from SCM |

**Record attestation — request body:**
```json
{
  "type": "junit",
  "status": "PASSED",
  "description": "All 247 tests passed",
  "metadata": { "total": 247, "failed": 0 }
}
```

**Pull request attestation — request body:**
```json
{
  "organisationSlug": "acme-corp",
  "provider": "github",
  "repositoryOwner": "my-org",
  "repositoryName": "my-service",
  "prNumber": 42
}
```

---

### Compliance Assertion

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/assert` | Assert whether an artifact is compliant with a flow |

**Request body:**
```json
{
  "flowId": "uuid",
  "sha256Digest": "sha256:e3b0c44..."
}
```

**Response:**
```json
{
  "compliant": true,
  "flowId": "uuid",
  "sha256Digest": "sha256:e3b0c44...",
  "checkedAt": "2025-01-01T10:00:00Z",
  "attestations": [
    { "type": "junit", "status": "PASSED" },
    { "type": "snyk",  "status": "PASSED" }
  ],
  "missingAttestations": []
}
```

---

### Environments

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/environments` | Register a new environment |
| `GET` | `/api/v1/environments` | List all environments |
| `GET` | `/api/v1/environments/{id}` | Get environment by ID |
| `PUT` | `/api/v1/environments/{id}` | Update an environment |
| `DELETE` | `/api/v1/environments/{id}` | Delete an environment |
| `POST` | `/api/v1/environments/{id}/snapshots` | Record a snapshot |
| `GET` | `/api/v1/environments/{id}/snapshots` | List snapshots |
| `GET` | `/api/v1/environments/{id}/snapshots/latest` | Get the latest snapshot |
| `GET` | `/api/v1/environments/{id}/snapshots/{index}` | Get snapshot by index |
| `GET` | `/api/v1/environments/{id}/diff` | Diff two snapshots (query params: `from`, `to`) |
| `POST` | `/api/v1/environments/{id}/baselines` | Create a baseline |
| `GET` | `/api/v1/environments/{id}/baselines/current` | Get the current baseline |
| `GET` | `/api/v1/environments/{id}/drift` | Check drift against baseline |
| `GET` | `/api/v1/environments/{id}/drift/history` | List drift reports |
| `POST` | `/api/v1/environments/{id}/allowlist` | Add an allow-list entry |
| `GET` | `/api/v1/environments/{id}/allowlist` | List allow-list entries |
| `DELETE` | `/api/v1/environments/{id}/allowlist/{entryId}` | Remove an allow-list entry |

---

### Logical Environments

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/logical-environments` | Create a logical environment |
| `GET` | `/api/v1/logical-environments` | List logical environments |
| `GET` | `/api/v1/logical-environments/{id}` | Get logical environment by ID |
| `PUT` | `/api/v1/logical-environments/{id}` | Update a logical environment |
| `DELETE` | `/api/v1/logical-environments/{id}` | Delete a logical environment |

---

### Approvals

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails/{trailId}/approvals` | Request approval for a trail |
| `GET` | `/api/v1/trails/{trailId}/approvals` | List approvals for a trail |
| `GET` | `/api/v1/approvals` | List all approvals (optional query param: `status`) |
| `GET` | `/api/v1/approvals/{approvalId}` | Get approval by ID |
| `POST` | `/api/v1/approvals/{approvalId}/approve` | Approve a request |
| `POST` | `/api/v1/approvals/{approvalId}/reject` | Reject a request |

---

### Deployment Policies & Gate

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/deployment-policies` | Create a deployment policy |
| `GET` | `/api/v1/deployment-policies` | List all deployment policies |
| `GET` | `/api/v1/deployment-policies/{id}` | Get policy by ID |
| `PUT` | `/api/v1/deployment-policies/{id}` | Update a policy |
| `DELETE` | `/api/v1/deployment-policies/{id}` | Delete a policy |
| `POST` | `/api/v1/gate/evaluate` | Evaluate the deployment gate |
| `GET` | `/api/v1/gate/results` | List recent gate evaluation results |

---

### Policies & Policy Attachments

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/policies` | Create a policy |
| `GET` | `/api/v1/policies` | List all policies |
| `GET` | `/api/v1/policies/{id}` | Get policy by ID |
| `PUT` | `/api/v1/policies/{id}` | Update a policy |
| `DELETE` | `/api/v1/policies/{id}` | Delete a policy |
| `POST` | `/api/v1/policy-attachments` | Attach a policy to an environment |
| `GET` | `/api/v1/policy-attachments` | List policy attachments |
| `GET` | `/api/v1/policy-attachments/{id}` | Get policy attachment by ID |
| `DELETE` | `/api/v1/policy-attachments/{id}` | Remove a policy attachment |

---

### OPA Policy Integration

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/opa/bundles` | Upload a Rego policy bundle (multipart) |
| `GET` | `/api/v1/opa/bundles` | List all policy bundles |
| `GET` | `/api/v1/opa/bundles/{id}` | Get a bundle by ID |
| `PUT` | `/api/v1/opa/bundles/{id}/activate` | Activate a bundle |
| `POST` | `/api/v1/opa/evaluate` | Evaluate artifact against active OPA policy |
| `GET` | `/api/v1/opa/decisions` | List policy decisions (audit trail) |
| `GET` | `/api/v1/opa/decisions/{id}` | Get policy decision by ID |

---

### Security Scans

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails/{trailId}/security-scans` | Record a security scan result |
| `GET` | `/api/v1/trails/{trailId}/security-scans` | List security scans for a trail |
| `GET` | `/api/v1/security-scans/{id}` | Get security scan by ID |
| `GET` | `/api/v1/security-scans/summary` | Get aggregated security scan summary |

---

### Regulatory Frameworks & Compliance

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/frameworks` | Create a regulatory framework |
| `GET` | `/api/v1/frameworks` | List all frameworks |
| `GET` | `/api/v1/frameworks/{id}` | Get framework with controls |
| `POST` | `/api/v1/frameworks/{id}/controls` | Add a control to a framework |
| `POST` | `/api/v1/compliance/mappings` | Create a compliance mapping |
| `GET` | `/api/v1/compliance/mappings` | List all compliance mappings |
| `POST` | `/api/v1/compliance/assess` | Run a compliance assessment |
| `GET` | `/api/v1/compliance/assessments` | List assessments |
| `GET` | `/api/v1/compliance/assessments/{id}` | Get assessment by ID |
| `GET` | `/api/v1/compliance/artifact/{sha256}` | Get chain of custody for an artifact |
| `GET` | `/api/v1/reports/regulatory/{frameworkId}` | Generate regulatory report |

---

### Organisations

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/organisations` | Create an organisation |
| `GET` | `/api/v1/organisations` | List all organisations |
| `GET` | `/api/v1/organisations/{slug}` | Get organisation by slug |
| `PUT` | `/api/v1/organisations/{slug}` | Update an organisation |
| `DELETE` | `/api/v1/organisations/{slug}` | Delete an organisation |
| `GET` | `/api/v1/organisations/{slug}/flows` | List flows in an organisation |
| `POST` | `/api/v1/organisations/{slug}/scm-integrations` | Register SCM integration |
| `GET` | `/api/v1/organisations/{slug}/scm-integrations` | List SCM integrations |
| `DELETE` | `/api/v1/organisations/{slug}/scm-integrations/{provider}` | Delete SCM integration |

### Organisation Members

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/organisations/{slug}/members` | List members |
| `POST` | `/api/v1/organisations/{slug}/members` | Invite a user (with role) |
| `GET` | `/api/v1/organisations/{slug}/members/{userId}` | Get member by ID |
| `PUT` | `/api/v1/organisations/{slug}/members/{userId}` | Update member role |
| `DELETE` | `/api/v1/organisations/{slug}/members/{userId}` | Remove a member |

---

### Users & Service Accounts

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/users` | Create a user |
| `GET` | `/api/v1/users` | List users |
| `GET` | `/api/v1/users/{id}` | Get user by ID |
| `PUT` | `/api/v1/users/{id}` | Update a user |
| `DELETE` | `/api/v1/users/{id}` | Delete a user |
| `POST` | `/api/v1/service-accounts` | Create a service account |
| `GET` | `/api/v1/service-accounts` | List service accounts |
| `GET` | `/api/v1/service-accounts/{id}` | Get service account |
| `PUT` | `/api/v1/service-accounts/{id}` | Update a service account |
| `DELETE` | `/api/v1/service-accounts/{id}` | Delete service account (and its keys) |
| `POST` | `/api/v1/service-accounts/{id}/api-keys` | Generate API key for service account (returned once) |
| `GET` | `/api/v1/service-accounts/{id}/api-keys` | List API keys (metadata only) |
| `DELETE` | `/api/v1/service-accounts/{id}/api-keys/{keyId}` | Revoke a service account API key |

### API Keys

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/api-keys` | Create a personal API key (returned once) |
| `GET` | `/api/v1/api-keys/owners/{ownerId}` | List API keys for an owner |
| `DELETE` | `/api/v1/api-keys/{id}/revoke` | Revoke an API key |

---

### SSO

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/organisations/{slug}/sso` | Create SSO configuration (OIDC) |
| `GET` | `/api/v1/organisations/{slug}/sso` | Get SSO configuration |
| `PUT` | `/api/v1/organisations/{slug}/sso` | Update SSO configuration |
| `DELETE` | `/api/v1/organisations/{slug}/sso` | Delete SSO configuration |
| `POST` | `/api/v1/organisations/{slug}/sso/test` | Test OIDC connection |
| `GET` | `/api/v1/organisations/{slug}/sso/login` | Initiate SSO login (returns IdP auth URL) |
| `GET` | `/api/v1/organisations/{slug}/sso/callback` | OIDC callback handler |

---

### Vault Evidence *(requires `vault.enabled=true`)*

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/evidence/{entityType}/{entityId}` | Store evidence in Vault |
| `GET` | `/api/v1/evidence/{entityType}/{entityId}` | Retrieve evidence metadata (query: `evidenceType`) |
| `GET` | `/api/v1/evidence/{entityType}/{entityId}/list` | List evidence types for an entity |
| `GET` | `/api/v1/evidence/{entityType}/{entityId}/download` | Download evidence artifact |
| `DELETE` | `/api/v1/evidence/{entityType}/{entityId}` | Soft-delete evidence |
| `GET` | `/api/v1/evidence/health` | Vault connectivity health check |

---

### Audit & Search

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/audit` | Query audit events (params: `eventType`, `trailId`, `actor`, `from`, `to`, `page`, `size`, `sortDesc`) |
| `GET` | `/api/v1/audit/{id}` | Get audit event by ID |
| `GET` | `/api/v1/search` | Cross-entity full-text search (required param: `q`; optional param: `type`) |

---

### Reports & Metrics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/reports/compliance` | Per-flow compliance summary (optional: `flowId`, `from`, `to`) |
| `GET` | `/api/v1/reports/audit-trail/{trailId}` | Full audit trail export for a trail |
| `GET` | `/api/v1/metrics/compliance` | Compliance metrics summary |
| `GET` | `/api/v1/metrics/security` | Security metrics summary |
| `GET` | `/api/v1/dashboard/stats` | Aggregate dashboard statistics |

---

### Notifications

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notifications` | List in-app notifications (optional: `isRead`, `severity`) |
| `GET` | `/api/v1/notifications/unread-count` | Get unread notification count |
| `POST` | `/api/v1/notifications/{id}/read` | Mark a notification as read |
| `POST` | `/api/v1/notifications/read-all` | Mark all notifications as read |
| `POST` | `/api/v1/notification-rules` | Create a notification rule |
| `GET` | `/api/v1/notification-rules` | List notification rules |
| `GET` | `/api/v1/notification-rules/{id}` | Get notification rule by ID |
| `PUT` | `/api/v1/notification-rules/{id}` | Update a notification rule |
| `DELETE` | `/api/v1/notification-rules/{id}` | Delete a notification rule |
| `POST` | `/api/v1/notification-rules/{id}/test` | Send a test notification |
| `GET` | `/api/v1/notification-rules/{id}/deliveries` | Get delivery history |

---

### Webhooks

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/webhooks/{source}` | Receive incoming webhook (GitHub and generic via `X-Hub-Signature-256`) |
| `POST` | `/api/v1/webhook-configs` | Register a webhook configuration |
| `GET` | `/api/v1/webhook-configs` | List webhook configurations |
| `DELETE` | `/api/v1/webhook-configs/{id}` | Delete webhook configuration |
| `GET` | `/api/v1/webhook-configs/{id}/deliveries` | List recent webhook deliveries |

---

### Slack Integration

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/organisations/{slug}/slack` | Configure Slack integration |
| `GET` | `/api/v1/organisations/{slug}/slack` | Get Slack configuration |
| `DELETE` | `/api/v1/organisations/{slug}/slack` | Remove Slack integration |
| `POST` | `/api/v1/organisations/{slug}/slack/commands` | Handle Slack slash commands |
| `POST` | `/api/v1/organisations/{slug}/slack/actions` | Handle Slack interactive actions |
| `POST` | `/api/v1/organisations/{slug}/slack/notify/trail-non-compliant` | Notify Slack of non-compliant trail |
| `POST` | `/api/v1/organisations/{slug}/slack/notify/approval-requested` | Notify Slack of approval request |

---

### Atlassian Integration (Jira & Confluence)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/integrations/jira/config` | Configure Jira integration |
| `GET` | `/api/v1/integrations/jira/config` | Get Jira configuration |
| `POST` | `/api/v1/integrations/jira/test` | Test Jira connectivity |
| `POST` | `/api/v1/integrations/jira/sync` | Manual sync of fact store events to Jira |
| `GET` | `/api/v1/integrations/jira/tickets` | List Jira tickets created by Factstore |
| `POST` | `/api/v1/integrations/jira/tickets` | Create a Jira ticket for a trail |
| `POST` | `/api/v1/integrations/confluence/config` | Configure Confluence integration |
| `GET` | `/api/v1/integrations/confluence/config` | Get Confluence configuration |
| `POST` | `/api/v1/integrations/confluence/test` | Test Confluence connectivity |

---

### Immutable Ledger *(requires `ledger.enabled=true`)*

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/ledger/entries` | List ledger entries (params: `page`, `size`) |
| `GET` | `/api/v1/ledger/entries/{recordId}` | Get ledger entry for a record |
| `POST` | `/api/v1/ledger/verify/{recordId}` | Verify integrity of a record |
| `POST` | `/api/v1/ledger/verify-chain` | Verify chain integrity for a date range |
| `GET` | `/api/v1/ledger/status` | Ledger health and sync status |

---

### V2 Attestations

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v2/attestations/{org}/{flow}/{trail}/{artifactFingerprint}` | Record an attestation in v2 format |

---

*For a guided walkthrough of the API, see [USER_GUIDE.md](../USER_GUIDE.md). For CI/CD integration examples, see [ci-integration.md](./ci-integration.md).*
