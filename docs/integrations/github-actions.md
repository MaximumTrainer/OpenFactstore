# GitHub Actions Integration

Factstore provides two reusable GitHub Actions workflows that you can call from your own pipelines to record attestations and enforce deployment gates.

## Prerequisites

- A running Factstore instance accessible from your GitHub Actions runners
- A Factstore API token stored as a GitHub Actions secret (e.g. `FACTSTORE_TOKEN`)

## Workflows

### `factstore-attest.yml` — Record an Attestation

Use this workflow to record evidence that a CI/CD stage completed successfully (e.g. unit tests passed, security scan clean).

**Inputs:**

| Input | Required | Default | Description |
|---|---|---|---|
| `trail-id` | ✅ | — | Trail ID (UUID) to attach the attestation to |
| `attestation-type` | ✅ | — | Type label, e.g. `unit-test`, `security-scan`, `container-scan` |
| `status` | ❌ | `PASSED` | `PASSED` or `FAILED` |
| `details` | ❌ | `""` | Free-text details about the attestation |
| `factstore-url` | ✅ | — | Base URL of your Factstore instance |

**Secrets:**

| Secret | Required | Description |
|---|---|---|
| `factstore-token` | ✅ | Bearer token for Factstore API authentication |

**Example usage:**

```yaml
jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run tests
        run: ./gradlew test

  attest-unit-test:
    needs: unit-test
    uses: your-org/factstore/.github/workflows/factstore-attest.yml@main
    with:
      trail-id: ${{ vars.FACTSTORE_TRAIL_ID }}
      attestation-type: unit-test
      status: PASSED
      factstore-url: https://factstore.your-org.com
    secrets:
      factstore-token: ${{ secrets.FACTSTORE_TOKEN }}
```

---

### `factstore-gate.yml` — Evaluate a Deployment Gate

Use this workflow before deploying to check that an artifact meets all policy requirements. The workflow outputs `allowed` and `reason`, and can optionally fail the pipeline if the gate blocks deployment.

**Inputs:**

| Input | Required | Default | Description |
|---|---|---|---|
| `artifact-sha256` | ✅ | — | SHA-256 digest of the container image or artifact |
| `environment` | ❌ | `""` | Target environment name (e.g. `production`, `staging`) |
| `flow-id` | ❌ | `""` | Flow ID for policy lookup |
| `factstore-url` | ✅ | — | Base URL of your Factstore instance |
| `fail-on-block` | ❌ | `true` | Set to `false` to allow blocked gates without failing the workflow |

**Secrets:**

| Secret | Required | Description |
|---|---|---|
| `factstore-token` | ✅ | Bearer token for Factstore API authentication |

**Outputs:**

| Output | Description |
|---|---|
| `allowed` | `true` if deployment is permitted, `false` otherwise |
| `reason` | Human-readable explanation of the gate decision |

**Example usage:**

```yaml
jobs:
  gate-check:
    uses: your-org/factstore/.github/workflows/factstore-gate.yml@main
    with:
      artifact-sha256: ${{ needs.build.outputs.image-sha256 }}
      environment: production
      flow-id: ${{ vars.FACTSTORE_FLOW_ID }}
      factstore-url: https://factstore.your-org.com
      fail-on-block: 'true'
    secrets:
      factstore-token: ${{ secrets.FACTSTORE_TOKEN }}

  deploy:
    needs: gate-check
    runs-on: ubuntu-latest
    steps:
      - name: Deploy
        run: echo "Deploying artifact (gate allowed: ${{ needs.gate-check.outputs.allowed }})"
```

---

## Full Pipeline Example

The following example shows a complete pipeline: build → test → attest → gate → deploy.

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      image-sha256: ${{ steps.build.outputs.sha256 }}
      trail-id: ${{ steps.trail.outputs.trail-id }}
    steps:
      - uses: actions/checkout@v4

      - name: Create Factstore Trail
        id: trail
        run: |
          TRAIL=$(curl -s -f -X POST "${{ vars.FACTSTORE_URL }}/api/v1/trails" \
            -H "Authorization: Bearer ${{ secrets.FACTSTORE_TOKEN }}" \
            -H "Content-Type: application/json" \
            -d "{
              \"flowId\": \"${{ vars.FACTSTORE_FLOW_ID }}\",
              \"gitCommitSha\": \"${{ github.sha }}\",
              \"gitBranch\": \"${{ github.ref_name }}\",
              \"gitAuthor\": \"${{ github.actor }}\"
            }")
          echo "trail-id=$(echo $TRAIL | jq -r '.id')" >> $GITHUB_OUTPUT

      - name: Build container image
        id: build
        run: |
          docker build -t myapp:${{ github.sha }} .
          SHA256=$(docker inspect --format='{{index .RepoDigests 0}}' myapp:${{ github.sha }} | cut -d: -f2)
          echo "sha256=$SHA256" >> $GITHUB_OUTPUT

  unit-test:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew test

  attest-tests:
    needs: [build, unit-test]
    uses: your-org/factstore/.github/workflows/factstore-attest.yml@main
    with:
      trail-id: ${{ needs.build.outputs.trail-id }}
      attestation-type: unit-test
      status: PASSED
      factstore-url: ${{ vars.FACTSTORE_URL }}
    secrets:
      factstore-token: ${{ secrets.FACTSTORE_TOKEN }}

  gate:
    needs: [build, attest-tests]
    uses: your-org/factstore/.github/workflows/factstore-gate.yml@main
    with:
      artifact-sha256: ${{ needs.build.outputs.image-sha256 }}
      environment: production
      flow-id: ${{ vars.FACTSTORE_FLOW_ID }}
      factstore-url: ${{ vars.FACTSTORE_URL }}
      fail-on-block: 'true'
    secrets:
      factstore-token: ${{ secrets.FACTSTORE_TOKEN }}

  deploy:
    needs: gate
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Deploy to production
        run: echo "Deploying (gate passed)!"
```

---

## Environment Variable Setup

Configure the following at the repository or organisation level:

| Name | Where | Description |
|---|---|---|
| `FACTSTORE_URL` | Variable | Base URL of your Factstore instance, e.g. `https://factstore.your-org.com` |
| `FACTSTORE_FLOW_ID` | Variable | UUID of the Factstore Flow that governs your pipeline |
| `FACTSTORE_TOKEN` | Secret | API bearer token — keep this secret |
