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

`TRAIL_ID` is the trail from [Part 5](./05-trails.md).

### JUnit test results

Keep the attestation's `id`; the evidence upload below attaches a file to it:

```bash
ATTESTATION_ID=$(curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "junit",
    "status": "PASSED",
    "details": "156 tests passed, 0 failed, 2 skipped"
  }' | jq -r .id)
echo "$ATTESTATION_ID"
```

### Snyk security scan

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "snyk",
    "status": "PASSED",
    "details": "No critical or high vulnerabilities found"
  }' | jq .
```

### Failed attestation

A gate that failed is recorded the same way, with `"status": "FAILED"`. (Not run here: it would make this tutorial's trail non-compliant.)

<!-- docs-e2e:skip -->
```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "snyk",
    "status": "FAILED",
    "details": "3 critical vulnerabilities found in lodash@4.17.20"
  }' | jq .
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
mkdir -p test-results
printf '<testsuite name="unit" tests="156" failures="0" errors="0" skipped="2"/>\n' > test-results/junit-report.xml

curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations/$ATTESTATION_ID/evidence" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -F "file=@./test-results/junit-report.xml" | jq .
```

**Response:**

```json
{
  "id": "e5f6a7b8-0000-0000-0000-000000000005",
  "attestationId": "d4e5f6a7-0000-0000-0000-000000000004",
  "fileName": "junit-report.xml",
  "sha256Hash": "f9e2f4b4eda9314261f98bf3488d873343d34ea1ecf9154b9de4a981ac83e327",
  "fileSizeBytes": 73,
  "contentType": "application/xml",
  "storedAt": "2026-03-11T10:16:00Z",
  "externalUrl": null
}
```

The evidence file is stored in the **Evidence Vault** and addressed by its SHA-256 hash, making it tamper-evident. For a `junit` attestation the XML is also parsed: the attestation's `details` become the counts (`{"tests":156,"failures":0,...}`) and its status follows the failures.

## List attestations for a Trail

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" | jq '.items[] | {type, status}'
```

The list is paginated: `items`, plus `page`, `size`, `totalItems` and `totalPages`.

## Checking compliance

After recording attestations, ask Factstore whether the artifact (`DIGEST` from [Part 6](./06-artifacts.md)) meets its Flow's requirements. `trailId` is optional: without it, the most recent trail carrying the digest decides. `jq -e` makes the command fail unless the answer is `COMPLIANT`, so it works as a deployment gate as written:

```bash
curl -s -X POST "$BASE_URL/api/v1/assert" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"flowId\": \"$FLOW_ID\",
    \"sha256Digest\": \"$DIGEST\",
    \"trailId\": \"$TRAIL_ID\"
  }" | jq -e '.status == "COMPLIANT"'
```

**Compliant response:**

```json
{
  "sha256Digest": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "flowId": "a1b2c3d4-0000-0000-0000-000000000001",
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "status": "COMPLIANT",
  "missingAttestationTypes": [],
  "failedAttestationTypes": [],
  "details": "All required attestations passed"
}
```

**Non-compliant response (missing snyk, junit failed):**

```json
{
  "sha256Digest": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "flowId": "a1b2c3d4-0000-0000-0000-000000000001",
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "status": "NON_COMPLIANT",
  "missingAttestationTypes": ["snyk"],
  "failedAttestationTypes": ["junit"],
  "details": "Missing required attestations: snyk; failed: junit"
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
