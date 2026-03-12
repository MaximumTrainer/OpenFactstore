# Part 4: Flows

A **Flow** represents a repeatable software delivery process that you want to track. Think of it as the definition of your pipeline: what it is called, what it produces, and what compliance evidence it must gather on every run.

Every Trail (a single pipeline execution) belongs to exactly one Flow.

## Create a Flow

```bash
curl -s -X POST "$BASE_URL/api/v1/flows" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "backend-ci",
    "description": "CI pipeline for the backend service",
    "requiredAttestationTypes": ["junit", "snyk"]
  }'
```

**Response:**

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

> **Compliance template:** Kosli uses a YAML template file to define required attestations. Factstore currently uses the `requiredAttestationTypes` array. A full YAML template spec is on the roadmap.

## List all Flows

```bash
curl -s "$BASE_URL/api/v1/flows"
```

## Get a Flow by ID

```bash
FLOW_ID=a1b2c3d4-0000-0000-0000-000000000001

curl -s "$BASE_URL/api/v1/flows/$FLOW_ID"
```

## Update a Flow

You can update the description or required attestation types. The Flow name is immutable once created.

```bash
curl -s -X PUT "$BASE_URL/api/v1/flows/$FLOW_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "CI + security scan pipeline for backend",
    "requiredAttestationTypes": ["junit", "snyk", "pull-request"]
  }'
```

## Delete a Flow

```bash
curl -s -X DELETE "$BASE_URL/api/v1/flows/$FLOW_ID"
# Returns HTTP 204 No Content
```

> ⚠️ Deleting a Flow does **not** automatically delete associated Trails or Artifacts. Referential integrity enforcement is on the roadmap.

## Compliance and required attestations

When you include attestation types in `requiredAttestationTypes`, Factstore will check that every Trail under this Flow has a **PASSED** attestation of each required type before declaring the Trail compliant.

For example, a Flow with `"requiredAttestationTypes": ["junit", "snyk"]` requires:
- A `junit` attestation with status `PASSED`
- A `snyk` attestation with status `PASSED`

If either is missing or has status `FAILED`, the compliance assertion returns `COMPLIANT: false`.

---

Previous: [← Part 3: Authentication](./03-authentication.md) | Next: [Part 5: Trails →](./05-trails.md)
