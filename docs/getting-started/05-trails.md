# Part 5: Trails

A **Trail** is a single execution of a Flow. Every time your CI pipeline runs — triggered by a commit, a pull request, or a release tag — you create a Trail to record everything that happens during that run.

Trails are uniquely identified within a Flow. Typically you use the Git commit SHA as the Trail's logical identity, but Factstore internally assigns each Trail a UUID.

## Begin a Trail

```bash
FLOW_ID=a1b2c3d4-0000-0000-0000-000000000001

curl -s -X POST "$BASE_URL/api/v1/trails" \
  -H "Content-Type: application/json" \
  -d "{
    \"flowId\": \"$FLOW_ID\",
    \"gitCommitSha\": \"e67f2f2b121f9325ebf166b7b3c707f73cb48b14\",
    \"gitBranch\": \"main\",
    \"gitAuthor\": \"Jane Smith\",
    \"gitAuthorEmail\": \"jane@example.com\",
    \"pullRequestId\": \"42\",
    \"pullRequestReviewer\": \"bob@example.com\",
    \"deploymentActor\": \"ci-runner@github\"
  }"
```

**Response:**

```json
{
  "id": "b2c3d4e5-0000-0000-0000-000000000002",
  "flowId": "a1b2c3d4-0000-0000-0000-000000000001",
  "gitCommitSha": "e67f2f2b121f9325ebf166b7b3c707f73cb48b14",
  "gitBranch": "main",
  "gitAuthor": "Jane Smith",
  "gitAuthorEmail": "jane@example.com",
  "pullRequestId": "42",
  "pullRequestReviewer": "bob@example.com",
  "deploymentActor": "ci-runner@github",
  "status": "IN_PROGRESS",
  "createdAt": "2026-03-11T10:05:00Z",
  "updatedAt": "2026-03-11T10:05:00Z"
}
```

### Trail fields

| Field | Type | Required | Description |
|---|---|---|---|
| `flowId` | UUID | ✅ | The Flow this Trail belongs to |
| `gitCommitSha` | string | ✅ | Full SHA-1 of the triggering commit |
| `gitBranch` | string | ✅ | Branch name (e.g. `main`, `feature/my-feature`) |
| `gitAuthor` | string | ✅ | Display name of the commit author |
| `gitAuthorEmail` | string | ✅ | Email of the commit author |
| `pullRequestId` | string | — | PR/MR number if this run was triggered by a pull request |
| `pullRequestReviewer` | string | — | Identity of the PR reviewer |
| `deploymentActor` | string | — | Identity of the system or person triggering deployment |

### Trail status

A Trail's `status` field reflects its lifecycle:

| Status | Meaning |
|---|---|
| `IN_PROGRESS` | Trail has been created; attestations are still being recorded |
| `COMPLIANT` | All required attestations are present and passed |
| `NON_COMPLIANT` | One or more required attestations are missing or failed |

## Defining Trail scope

You decide what constitutes a single Trail execution. Common patterns:

- **Per commit** — create one Trail per git commit SHA (most common for CI pipelines)
- **Per pull request** — create one Trail per PR, updating it across multiple commits
- **Per issue/ticket** — create one Trail per Jira or GitHub issue, spanning multiple PRs

## List all Trails

```bash
curl -s "$BASE_URL/api/v1/trails"
```

Filter by Flow:

```bash
curl -s "$BASE_URL/api/v1/trails?flowId=$FLOW_ID"
```

## List Trails for a specific Flow

```bash
curl -s "$BASE_URL/api/v1/flows/$FLOW_ID/trails"
```

## Get a Trail by ID

```bash
TRAIL_ID=b2c3d4e5-0000-0000-0000-000000000002

curl -s "$BASE_URL/api/v1/trails/$TRAIL_ID"
```

## Using the Trail ID in subsequent steps

After creating a Trail you will use its `id` in every subsequent call to attach artifacts and attestations:

```bash
export TRAIL_ID=b2c3d4e5-0000-0000-0000-000000000002
```

---

Previous: [← Part 4: Flows](./04-flows.md) | Next: [Part 6: Artifacts →](./06-artifacts.md)
