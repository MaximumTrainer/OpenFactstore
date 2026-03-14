# Part 9: Policies

**Policies** define the compliance requirements an artifact must satisfy before it is allowed into a specific environment. They turn Factstore from a *visibility* tool into an *enforcement* tool.

A policy can require:
- **Provenance** — the artifact must have been recorded in a Factstore Flow
- **Specific attestations** — e.g. a `snyk` scan attestation must have passed
- **Approvals** — a human approval must have been granted (see [Part 10: Approvals](./10-approvals.md))

---

## Create a deployment policy

```bash
curl -s -X POST "$BASE_URL/api/v1/policies" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-requirements",
    "description": "Production deployment requirements",
    "flowId": "'$FLOW_ID'",
    "enforceProvenance": true,
    "enforceApprovals": true,
    "requiredAttestationTypes": ["snyk", "junit", "pull-request"]
  }'
```

Store the returned `id` as `POLICY_ID`.

---

## List policies

```bash
curl -s "$BASE_URL/api/v1/policies"
```

---

## Update a policy

```bash
curl -s -X PUT "$BASE_URL/api/v1/policies/$POLICY_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "requiredAttestationTypes": ["snyk", "junit", "pull-request", "owasp-zap"],
    "isActive": true
  }'
```

---

## Deployment gate

The **deployment gate** is the active enforcement point. Before an artifact is deployed to an environment, your pipeline calls the gate endpoint. If the artifact fails any active policy, the gate returns `BLOCKED` and your pipeline should halt.

### Evaluate the gate

```bash
curl -s -X POST "$BASE_URL/api/v1/gate/evaluate" \
  -H "Content-Type: application/json" \
  -d '{
    "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
    "environmentId": "'$ENV_ID'",
    "requestedBy": "ci-pipeline"
  }'
```

**Blocked response:**

```json
{
  "decision": "BLOCKED",
  "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "environmentId": "env-uuid-production",
  "evaluatedAt": "2026-03-11T12:00:00Z",
  "blockReasons": [
    "Missing attestation: snyk (policy: prod-requirements)",
    "No approved release approval found (policy: prod-requirements)"
  ],
  "policiesEvaluated": 1
}
```

**Allowed response:**

```json
{
  "decision": "ALLOWED",
  "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "environmentId": "env-uuid-production",
  "evaluatedAt": "2026-03-11T12:00:00Z",
  "blockReasons": [],
  "policiesEvaluated": 1
}
```

> **Tip:** Allow-listed artifacts bypass all policy checks and always receive `ALLOWED`. See [Part 8: Environments](./08-environments.md) for allow-list management.

---

## Policy compliance states

| State | Meaning |
|---|---|
| `COMPLIANT` | All artifacts in the latest snapshot satisfy all attached policies |
| `NON_COMPLIANT` | One or more artifacts violate at least one attached policy |
| `UNKNOWN` | No policies are attached — compliance requirements are undefined |

---

Previous: [← Part 8: Environments](./08-environments.md) | Next: [Part 10: Approvals →](./10-approvals.md)
