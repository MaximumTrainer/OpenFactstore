# Part 7: Attestations

**Attestations** are the evidence records that prove your quality gates were executed. They are the heart of Factstore's compliance model — without attestations, a Trail cannot be compliant.

Attestations are **append-only** and **immutable**. Once recorded, they cannot be edited or deleted. You can record the same attestation type multiple times; only the latest record is used when evaluating compliance.

## Attestation types

Factstore accepts any string as an attestation type. Use a consistent naming convention across your organisation. Common types:

| Type string | Meaning |
|---|---|
| `junit` | JUnit or JUnit-compatible XML test report |
| `snyk` | Snyk security scan report |
| `pull-request` | Pull request review completed |
| `jira` | Linked Jira ticket |
| `sonar` | SonarQube/SonarCloud quality gate |
| `generic` | Any other evidence (free-form details) |

## Attestation status

| Status | Meaning |
|---|---|
| `PASSED` | The quality gate passed — counts as compliant evidence |
| `FAILED` | The quality gate failed — Trail is non-compliant |
| `PENDING` | Evidence has been received but not yet evaluated (default) |

## Record an attestation

### JUnit test results

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "junit",
    "status": "PASSED",
    "details": "156 tests passed, 0 failed, 2 skipped"
  }'
```

### Snyk security scan

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "snyk",
    "status": "PASSED",
    "details": "No critical or high vulnerabilities found"
  }'
```

### Failed attestation

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "snyk",
    "status": "FAILED",
    "details": "3 critical vulnerabilities found in lodash@4.17.20"
  }'
```

**Response:**

```json
{
  "id": "d4e5f6a7-0000-0000-0000-000000000004",
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "type": "snyk",
  "status": "FAILED",
  "evidenceFileHash": null,
  "evidenceFileName": null,
  "evidenceFileSizeBytes": null,
  "details": "3 critical vulnerabilities found in lodash@4.17.20",
  "createdAt": "2026-03-11T10:15:00Z"
}
```

## Uploading evidence files

You can attach a file (e.g. an XML test report, a JSON scan output, a PDF) as evidence for an attestation:

```bash
ATTESTATION_ID=d4e5f6a7-0000-0000-0000-000000000004

curl -s -X POST \
  "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations/$ATTESTATION_ID/evidence" \
  -F "file=@./test-results/junit-report.xml"
```

**Response:**

```json
{
  "id": "e5f6a7b8-0000-0000-0000-000000000005",
  "attestationId": "d4e5f6a7-0000-0000-0000-000000000004",
  "fileName": "junit-report.xml",
  "contentType": "application/xml",
  "sizeBytes": 48210,
  "sha256Hash": "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b",
  "createdAt": "2026-03-11T10:16:00Z"
}
```

The evidence file is stored in the **Evidence Vault** and addressed by its SHA-256 hash, making it tamper-evident.

## List attestations for a Trail

```bash
curl -s "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations"
```

## Checking compliance

After recording attestations, ask Factstore whether the Trail is compliant with its Flow's requirements:

```bash
curl -s -X POST "$BASE_URL/api/v1/assert" \
  -H "Content-Type: application/json" \
  -d "{
    \"trailId\": \"$TRAIL_ID\",
    \"artifactSha256\": \"sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f\"
  }"
```

**Compliant response:**

```json
{
  "compliant": true,
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "missingAttestationTypes": [],
  "failedAttestationTypes": []
}
```

**Non-compliant response (missing snyk, junit failed):**

```json
{
  "compliant": false,
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "artifactSha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "missingAttestationTypes": ["snyk"],
  "failedAttestationTypes": ["junit"]
}
```

### What makes a Trail compliant?

A Trail is **compliant** when:
1. Every attestation type listed in `requiredAttestationTypes` on the Flow has at least one attestation recorded on the Trail
2. All of those attestations have status `PASSED`

If any required attestation is missing or has status `FAILED`, the Trail is non-compliant.

### Attesting without a required-types list

If the Flow was created with an empty `requiredAttestationTypes`, compliance is determined solely by the attestations actually recorded — a Trail is compliant as long as none of its attestations have status `FAILED`.

---

Previous: [← Part 6: Artifacts](./06-artifacts.md) | Next: [Part 8: Environments →](./08-environments.md)
