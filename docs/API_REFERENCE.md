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

### Error body and correlation id

Every response carries an `X-Request-Id` header. Send your own (letters, digits, `.`, `_`, `:`, `-`, up to 128 characters), for example a CI run id, and it is used as-is; otherwise one is generated. Error responses repeat it in the body:

```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred. Quote the correlation id to an operator.",
  "timestamp": "2026-11-08T12:00:00Z",
  "correlationId": "3f1c2a9e-5b7d-4c1e-9a2f-8d6e4b0c1a77"
}
```

A `5xx` body never contains the exception's own text, class names or SQL; those are in the server log under the same correlation id. Quote the id when reporting a failure.

---

## Endpoints by Resource

### Flows

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/flows` | Create a new flow |
| `GET` | `/api/v1/flows` | List flows (query param: `includeArchived`, default `false`) |
| `GET` | `/api/v1/flows/{id}` | Get flow by ID |
| `PUT` | `/api/v1/flows/{id}` | Update a flow |
| `POST` | `/api/v1/flows/{id}/rename` | Rename a flow, preserving the old name for forwarding |
| `POST` | `/api/v1/flows/{id}/archive` | Archive a flow (soft delete) |
| `POST` | `/api/v1/flows/{id}/unarchive` | Unarchive a flow |
| `DELETE` | `/api/v1/flows/{id}` | Delete a flow |
| `GET` | `/api/v1/flows/{id}/impact` | How much existing evidence a change to this flow would affect |
| `GET` | `/api/v1/flows/{id}/template` | Get flow template as YAML |
| `GET` | `/api/v1/flows/{id}/template-drift` | Whether the flow still matches the template it came from |
| `POST` | `/api/v1/flows/{flowId}/security-thresholds` | Set security scan thresholds for a flow |
| `GET` | `/api/v1/flows/{flowId}/security-thresholds` | Get security scan thresholds for a flow |

**Create flow — request body:**
```json
{
  "name": "my-service-compliance",
  "description": "Optional description",
  "requiredAttestationTypes": ["junit", "snyk", "trivy"],
  "tags": { "team": "payments", "criticality": "high" },
  "templateYaml": "version: 1\nartifacts: []\n",
  "requiresApproval": false,
  "requiredApproverRoles": []
}
```

**Update flow — `PUT /api/v1/flows/{id}`:**

Every field is optional; only what is sent is changed. An absent field is left alone — so an
omitted `templateYaml` does *not* clear an existing template.

```json
{
  "name": "my-service-compliance",
  "description": "Updated description",
  "requiredAttestationTypes": ["junit", "snyk", "trivy", "ghas"],
  "tags": { "team": "payments" },
  "templateYaml": "version: 1\n",
  "requiresApproval": true,
  "requiredApproverRoles": ["release-manager"]
}
```

**Flow definition changes are audited.** Because a change to the required attestations changes how
every attached trail evaluates on its next assertion, each change writes an audit event carrying
the actor and a before/after diff of exactly the fields that changed:

| Event type | Written by |
|---|---|
| `FLOW_UPDATED` | `PUT /api/v1/flows/{id}` |
| `FLOW_RENAMED` | `POST /api/v1/flows/{id}/rename` |
| `FLOW_ARCHIVED` | `POST /api/v1/flows/{id}/archive` |
| `FLOW_UNARCHIVED` | `POST /api/v1/flows/{id}/unarchive` |

```json
{
  "flowId": "uuid",
  "flowName": "my-service-compliance",
  "changes": {
    "requiredAttestationTypes": {
      "before": ["junit", "snyk"],
      "after": ["junit", "snyk", "ghas"]
    }
  }
}
```

An update that changes nothing writes no event. A `templateYaml` change is recorded as a size
summary rather than the whole document.

**Flow impact — `GET /api/v1/flows/{id}/impact`:**
```json
{
  "flowId": "uuid",
  "flowName": "my-service-compliance",
  "trailCount": 12,
  "pendingTrailCount": 3
}
```

Call this before changing a flow's required attestations: every attached trail is judged against
the new definition on its next assertion. The web UI shows this as a warning on the edit form.

---

### Trails

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/trails` | Create (or join) a trail — supports `X-Factstore-CI-Context` header |
| `GET` | `/api/v1/trails` | List trails (optional query param: `flowId`) |
| `GET` | `/api/v1/trails/{id}` | Get trail by ID |
| `GET` | `/api/v1/trails/lookup` | Resolve a trail without its UUID |
| `GET` | `/api/v1/flows/{flowId}/trails` | List trails for a specific flow |
| `GET` | `/api/v1/trails/{id}/audit` | Get audit events for a trail |
| `POST` | `/api/v1/trails/{id}/assert` | Assert this pipeline execution — see [Compliance Assertion](#compliance-assertion) |
| `POST` | `/api/v1/trails/{id}/archive` | Archive a trail (soft delete, reversible) |
| `POST` | `/api/v1/trails/{id}/unarchive` | Restore an archived trail |
| `GET` | `/api/v1/trails/{id}/cascade` | What deleting this trail would remove |
| `DELETE` | `/api/v1/trails/{id}` | Permanently delete a trail and the evidence it owns |
| `POST` | `/api/v1/trails/cleanup` | Bulk cleanup by flow, tag or age (dry run by default) |

**Create trail — request body:**
```json
{
  "flowId": "uuid",
  "gitCommitSha": "abc123",
  "gitBranch": "main",
  "gitAuthor": "alice",
  "gitAuthorEmail": "alice@example.com",
  "pullRequestNumber": 42,
  "buildUrl": "https://ci.example.com/builds/123",
  "name": "nightly-release",
  "externalId": "my-org/my-repo@4711"
}
```

`externalId` is a stable release identifier (build number, run id, release tag), **unique per
flow**. Supplying it makes creation a get-or-create:

| Outcome | Status | Meaning |
|---|---|---|
| A new trail was created | `201 Created` | This call started the release |
| An existing trail was returned | `200 OK` | A trail for this release identifier already exists |

A re-run of the pipeline that owns the release therefore cannot fork the evidence, and other
pipelines can address the same trail by that identifier. Trails created without an `externalId`
are never deduplicated. The CQRS path `POST /api/v2/trails` behaves the same way and reports
`status: "created"` or `status: "exists"`.

**Look up a trail — `GET /api/v1/trails/lookup`:**

| Query param | Description |
|---|---|
| `flowId` | **Required.** The flow to search within |
| `externalId` | Resolve by release identifier |
| `name` | Resolve by trail name |
| `gitCommitSha` | Resolve the **most recent** trail for that commit |

Exactly one of `externalId`, `name` or `gitCommitSha` is used, in that order of precedence.
Returns a single `TrailResponse`, `404` when nothing matches, or `400` when no selector was given.

This is how a secondary pipeline — integration tests, API tests, environment testing — attaches
its attestations to the trail the primary pipeline created without any UUID plumbing. See
[ci-integration.md](./ci-integration.md#evidence-from-several-pipelines-on-one-trail) for a worked
multi-pipeline example.

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

### Metrics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/metrics/compliance` | Compliance metric summary |
| `GET` | `/api/v1/metrics/security` | Security scan metric summary |
| `GET` | `/api/v1/metrics/delivery` | Delivery metrics: DORA where derivable, plus gates (query param: `days`, default 30, clamped 1-365) |

**`GET /api/v1/metrics/delivery?days=30`:**

```json
{
  "windowDays": 30,
  "from": "2026-08-05T09:00:00Z",
  "to": "2026-09-04T09:00:00Z",
  "deploymentFrequency": {
    "value": 2.4, "unit": "per day", "sampleSize": 72, "available": true,
    "basis": "Deployments recorded to any environment, divided by the days in the window."
  },
  "leadTimeForChanges": { "value": 13.3, "unit": "hours", "sampleSize": 68, "available": true, "basis": "..." },
  "changeFailureRate":  { "value": 12.5, "unit": "percent", "sampleSize": 96, "available": true, "basis": "..." },
  "timeToRestoreService": { "value": null, "unit": "hours", "sampleSize": 0, "available": false, "basis": "..." },
  "gates": {
    "evaluations": 96, "allowed": 84, "blocked": 12, "blockRate": 12.5,
    "topBlockReasons": [{ "value": "missing attestation: snyk", "count": 7 }],
    "perDay": [{ "date": "2026-08-06", "allowed": 4, "blocked": 1 }]
  },
  "assertions": {
    "evaluations": 210, "compliant": 188, "blocked": 22, "blockRate": 10.48,
    "topMissingAttestations": [{ "value": "snyk", "count": 14 }]
  }
}
```

Every headline metric carries `available` and `basis`. **A metric that cannot honestly be derived
from what Factstore records reports `available: false` with `basis` explaining why**, rather than a
zero — a zero on a dashboard reads as "we are doing well"; an absent metric reads as "we do not
know", which is the truth. `timeToRestoreService` is the standing example: restoring service is an
incident-management event and no incident records are kept here.

Two of the four are deliberately labelled rather than assumed:

- **`leadTimeForChanges`** measures trail creation → deployment. The trail is the stand-in for the
  commit, because it is created by the pipeline run that builds the artifact. Deployments whose
  artifact has no trail are excluded from the sample.
- **`changeFailureRate`** is the share of *deployment gate* evaluations that **blocked** — a
  pre-deployment gate rate, not DORA's post-release change failure rate. Factstore records the gate
  decision, not what happened after a release shipped.

`gates.perDay` has one bucket per day in the window, empty days included, so a trend line does not
silently close the gaps where nothing shipped.

---

### Flow Templates

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/hub/templates` | List templates (query params: `category`=`SERVICE_TYPE`\|`FRAMEWORK`, `orgSlug`) |
| `GET` | `/api/v1/hub/templates/{id}` | Get a template by ID |
| `POST` | `/api/v1/hub/templates/compose` | Combine several templates into one |
| `GET` | `/api/v1/hub/templates/custom` | List an organisation's own templates |
| `POST` | `/api/v1/hub/templates/custom` | Publish an organisation template |
| `PUT` | `/api/v1/hub/templates/custom/{id}` | Update an organisation template |
| `DELETE` | `/api/v1/hub/templates/custom/{id}` | Withdraw an organisation template |

Two categories: `SERVICE_TYPE` templates are the baseline gates for a shape of service
(`service-public-api`, `service-internal`, `service-batch-job`, `service-frontend`); `FRAMEWORK`
templates are regulatory (`slsa-level-2`, `pci-dss-v4`, `sox-itgc`, `gdpr-art32`). A flow commonly
wants one of each — `POST /api/v1/flows` accepts `templateIds` and copies the merged template onto
the flow.

Full guide, including the composition and drift semantics and how to add templates:
**[flow-templates.md](./flow-templates.md)**.

---

### Retiring trails and flows

Trails are compliance evidence, so **archiving is the default way to retire one**: the record and
everything attached to it survive, the trail simply leaves the listings, and it is reversible.
`GET /api/v1/trails` and `GET /api/v1/flows/{flowId}/trails` hide archived trails unless
`includeArchived=true`.

**Deleting a trail** (`DELETE /api/v1/trails/{id}`) is permanent and cascades, in this order:

| Removed | Note |
|---|---|
| evidence files | deleted first: `evidence_files` has a foreign key onto `attestations` |
| security scans, compliance assessments | no foreign key onto `trails`; would otherwise be orphaned |
| Jira tickets, coverage reports, approvals | |
| attestations | |
| artifacts | build provenance cascades from these |
| the trail itself | |

Deliberately **not** removed:

- **the audit log** — `audit_events` has no foreign key onto `trails` by design, so the record that
  the evidence existed outlives the evidence. The deletion event is written *before* the rows go.
- **the append-only ledger** — entries are immutable.

The response reports exactly what was removed:

```json
{
  "trailId": "uuid",
  "cascade": {
    "attestations": 3, "artifacts": 1, "evidenceFiles": 2, "approvals": 0,
    "coverageReports": 0, "securityScans": 0, "complianceAssessments": 0,
    "jiraTickets": 0, "total": 6
  }
}
```

`GET /api/v1/trails/{id}/cascade` returns the same counts without removing anything, so a UI can
state the blast radius before asking for confirmation.

**Bulk cleanup** (`POST /api/v1/trails/cleanup`) is for tearing down evaluation and demo data:

```json
{
  "flowId": "uuid",
  "tagKey": "env",
  "tagValue": "demo",
  "olderThan": "2026-01-01T00:00:00Z",
  "mode": "ARCHIVE",
  "dryRun": true
}
```

At least one of `flowId`, `tagKey` or `olderThan` is **required** — a mistyped request cannot select
every trail in the system. `mode` defaults to `ARCHIVE` and `dryRun` to `true`, so the safe thing
happens when a field is forgotten. A dry run returns the same shape with `dryRun: true` and changes
nothing. Every archive and every deletion is written to the audit log as `TRAIL_ARCHIVED`,
`TRAIL_UNARCHIVED` or `TRAIL_DELETED`, with the actor and the cascade counts.

**Deleting a flow** (`DELETE /api/v1/flows/{id}`, and `/api/v2/flows/{id}`) is refused with `409`
while trails are still attached, because it would orphan the evidence recorded against it. Archive
the flow to retire it reversibly, or pass `?force=true` to delete it deliberately. The deletion is
audited as `FLOW_DELETED`.

---

### Compliance Assertion

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/assert` | Assert whether an artifact is compliant with a flow |
| `POST` | `/api/v1/trails/{id}/assert` | Assert a specific pipeline execution (no digest required) |

**Request body** (`POST /api/v1/assert`):
```json
{
  "flowId": "uuid",
  "sha256Digest": "sha256:e3b0c44...",
  "trailId": "uuid"
}
```

`trailId` is optional and scopes the verdict to a single pipeline execution. **CI pipelines should
always send it.** Without it, the *most recent* trail carrying that digest decides — a re-run against
an unchanged commit is therefore judged on its own evidence, not on the previous run's. A `trailId`
combined with a digest that belongs to a different trail is rejected with `400`.

**Request body** (`POST /api/v1/trails/{id}/assert`) — every field optional:
```json
{
  "flowId": "uuid",
  "sha256Digest": "sha256:e3b0c44..."
}
```

`flowId` defaults to the trail's own flow. Omitting `sha256Digest` judges the trail on its
attestations alone, which is what gates that run *before* the image is pushed (unit tests, SAST)
need. An empty body (`{}`) is valid.

**Response** (both endpoints):
```json
{
  "sha256Digest": "sha256:e3b0c44...",
  "flowId": "uuid",
  "trailId": "uuid",
  "status": "COMPLIANT",
  "missingAttestationTypes": [],
  "failedAttestationTypes": [],
  "missingAttestationNames": [],
  "failedAttestationNames": [],
  "details": "All required attestations passed"
}
```

`status` is `COMPLIANT` or `NON_COMPLIANT`. `trailId` names the execution the verdict was computed
from. Flows defined with `requiredAttestationTypes` populate the `*AttestationTypes` lists;
template-driven flows populate the `*AttestationNames` lists.

The assertion also writes its outcome back to the deciding trail's status (`COMPLIANT` /
`NON_COMPLIANT`) in the same transaction as the `GATE_ALLOWED` / `GATE_BLOCKED` audit event, so
trail status and audit log always agree.

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
