# Part 9: Policies

> 🚧 **This feature is not yet implemented.**
>
> Environment policies and deployment gates are planned features for Factstore. This page describes what they will do so you can understand how they fit in the overall model.
>
> **Tracked in:**
> - [Feature: Deployment Gate & Policy Engine — Issue #7](https://github.com/MaximumTrainer/Factstore/issues/7)
> - [Feature: Environment Diff & Drift Detection — Issue #9](https://github.com/MaximumTrainer/Factstore/issues/9)

---

## What policies will do

**Policies** define the compliance requirements an artifact must satisfy before it is allowed into a specific environment. They turn Factstore from a *visibility* tool into an *enforcement* tool.

A policy can require:
- **Provenance** — the artifact must have been recorded in a Factstore Flow
- **Trail compliance** — the artifact's Trail must be fully compliant
- **Specific attestations** — e.g. a `snyk` scan must have been recorded and passed
- **Approvals** — a human approval must have been granted (see [Part 10: Approvals](./10-approvals.md))

Policies are **versioned and immutable** — updating a policy creates a new version, so the history of what was required at any point in time is always available.

## Planned policy syntax

Policies will be defined in YAML:

```yaml
# prod-policy.yaml
_schema: https://factstore.io/schemas/policy/v1

artifacts:
  provenance:
    required: true          # artifact must be recorded in a Factstore Flow

  trail-compliance:
    required: true          # the artifact's Trail must be compliant

  attestations:
    - name: "*"             # any attestation name
      type: snyk            # must have a snyk attestation

    - name: unit-tests
      type: junit           # must have a junit attestation named unit-tests

    - name: "*"
      type: pull-request    # must have a pull-request attestation
```

### Creating a policy (planned)

```bash
curl -s -X POST "$BASE_URL/api/v1/policies" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-requirements",
    "description": "Production deployment requirements",
    "enforceProvenance": true,
    "enforceTrailCompliance": true,
    "requiredAttestationTypes": ["snyk", "junit", "pull-request"]
  }'
```

### Attaching a policy to an environment (planned)

```bash
curl -s -X POST "$BASE_URL/api/v1/environments/$ENV_ID/policies/$POLICY_ID"
```

An environment can have multiple policies attached; all must pass for an artifact to be compliant.

## Deployment gate

The **deployment gate** is the active enforcement point. Before an artifact is deployed to an environment, your pipeline calls the gate endpoint. If the artifact fails any attached policy, the gate returns `BLOCKED` and the pipeline should halt.

### Gate evaluation (planned)

```bash
curl -s -X POST "$BASE_URL/api/v1/gate/evaluate" \
  -H "Content-Type: application/json" \
  -d '{
    "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
    "environmentId": "env-uuid-production"
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
    "Missing attestation: snyk",
    "Approval status: PENDING_APPROVAL"
  ]
}
```

**Allowed response:**

```json
{
  "decision": "ALLOWED",
  "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "environmentId": "env-uuid-production",
  "evaluatedAt": "2026-03-11T12:00:00Z",
  "blockReasons": []
}
```

## Policy compliance states

| State | Meaning |
|---|---|
| `COMPLIANT` | All artifacts in the latest snapshot satisfy all attached policies |
| `NON_COMPLIANT` | One or more artifacts violate at least one attached policy |
| `UNKNOWN` | No policies are attached — compliance requirements are undefined |

## Current workaround

Until policies are implemented, use the existing compliance assertion endpoint as a manual gate in your pipeline:

```bash
RESULT=$(curl -s -X POST "$BASE_URL/api/v1/assert" \
  -H "Content-Type: application/json" \
  -d "{\"trailId\": \"$TRAIL_ID\", \"artifactSha256\": \"$DIGEST\"}")

COMPLIANT=$(echo "$RESULT" | jq -r '.compliant')

if [ "$COMPLIANT" != "true" ]; then
  echo "Artifact is not compliant — blocking deployment"
  exit 1
fi
```

---

Previous: [← Part 8: Environments](./08-environments.md) | Next: [Part 10: Approvals →](./10-approvals.md)
