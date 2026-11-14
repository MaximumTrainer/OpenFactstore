# Part 4: Flows

A **Flow** represents a repeatable software delivery process that you want to track. Think of it as the definition of your pipeline: what it is called, what it produces, and what compliance evidence it must gather on every run.

Every Trail (a single pipeline execution) belongs to exactly one Flow.

## Create a Flow

```bash
FLOW_ID=$(curl -s -X POST "$BASE_URL/api/v1/flows" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "backend-ci",
    "description": "CI pipeline for the backend service",
    "requiredAttestationTypes": ["junit", "snyk"]
  }' | jq -r .id)
echo "$FLOW_ID"
```

Keep `FLOW_ID`: the next parts use it. The full response:

```json
{
  "id": "a1b2c3d4-0000-0000-0000-000000000001",
  "name": "backend-ci",
  "description": "CI pipeline for the backend service",
  "requiredAttestationTypes": ["junit", "snyk"],
  "createdAt": "2026-03-11T10:00:00Z",
  "updatedAt": "2026-03-11T10:00:00Z"
}
```

### Flow fields

| Field | Type | Description |
|---|---|---|
| `name` | string (required) | Unique name for the flow |
| `description` | string | Human-readable description |
| `requiredAttestationTypes` | string[] | Attestation types that every Trail must satisfy for compliance (e.g. `junit`, `snyk`, `pull-request`) |

> **Templates:** A flow can also be defined by a YAML template (`templateYaml`) or created from the template catalogue (`templateIds`); see [Flow templates](../flow-templates.md).

## List all Flows

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/flows"
```

## Get a Flow by ID

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/flows/$FLOW_ID" | jq .
```

## Update a Flow

`PUT` changes only the fields you send: here the description. `requiredAttestationTypes`, `tags` and `requiresApproval` work the same way; `GET /api/v1/flows/{id}/impact` shows how much existing evidence a change to the requirements would affect first. To rename a flow, use `POST /api/v1/flows/{id}/rename`, which keeps the old name forwarding.

```bash
curl -s -X PUT "$BASE_URL/api/v1/flows/$FLOW_ID" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "CI + security scan pipeline for backend"
  }' | jq .description
```

## Delete a Flow

This example deletes a scratch flow, so `FLOW_ID` stays usable for the next parts:

```bash
SCRATCH_ID=$(curl -s -X POST "$BASE_URL/api/v1/flows" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "name": "scratch-flow" }' | jq -r .id)

curl -s -o /dev/null -w "%{http_code}\n" -X DELETE "$BASE_URL/api/v1/flows/$SCRATCH_ID" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN"
# 204
```

To retire a flow but keep it readable, archive it instead: `POST /api/v1/flows/{id}/archive`.

> ⚠️ Deleting a Flow does **not** automatically delete associated Trails or Artifacts. Referential integrity enforcement is on the roadmap.

## Compliance and required attestations

When you include attestation types in `requiredAttestationTypes`, Factstore will check that every Trail under this Flow has a **PASSED** attestation of each required type before declaring the Trail compliant.

For example, a Flow with `"requiredAttestationTypes": ["junit", "snyk"]` requires:
- A `junit` attestation with status `PASSED`
- A `snyk` attestation with status `PASSED`

If either is missing or has status `FAILED`, the compliance assertion returns `"status": "NON_COMPLIANT"`.

---

Previous: [← Part 3: Authentication](./03-authentication.md) | Next: [Part 5: Trails →](./05-trails.md)
