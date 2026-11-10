# CI/CD Integration Guide

This guide explains how to integrate Factstore into your CI/CD pipeline to automatically create compliance trails and record attestations as part of your build process.

## Authenticating a pipeline

Every request needs a credential: authentication is enforced by default. Give **each pipeline its
own service account and API key**, created with the `CI_PIPELINE` preset, and store the key as a
CI secret (`FACTSTORE_API_TOKEN` below).

| The preset grants | So a pipeline can |
|---|---|
| `trails:write`, `attestations:write`, `artifacts:write`, `evidence:write` | open trails, record attestations and artifacts, upload evidence |
| `assert:execute` | run compliance assertions and deployment gates |
| `flows:read`, `trails:read` | look up its flow by name, a trail by commit or identifier, and gate results |

It cannot create or delete flows, change policies, or mint keys. With an `admin` key:

```bash
SA_ID=$(curl -s -X POST "$FACTSTORE_URL/api/v1/service-accounts" \
  -H "Authorization: Bearer $ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"name":"payments-api-ci","description":"payments-api GitHub Actions"}' | jq -r .id)

curl -s -X POST "$FACTSTORE_URL/api/v1/api-keys" \
  -H "Authorization: Bearer $ADMIN_KEY" -H "Content-Type: application/json" \
  -d "{\"ownerId\":\"$SA_ID\",\"ownerType\":\"SERVICE_ACCOUNT\",\"label\":\"payments-api main\",\"preset\":\"CI_PIPELINE\",\"orgSlug\":\"payments\"}"
```

The response holds the key once, as `plainTextKey`. Binding it to the pipeline's organisation
(`orgSlug`) keeps it from seeing other organisations' data. Send it as
`Authorization: Bearer <key>`, always over https: plaintext HTTP from another machine is refused
with `426`. Keys expire after 90 days by default; rotate with
`POST /api/v1/api-keys/{id}/rotate`, which keeps the old key working for 24 hours. More in
[api-authorisation.md](api-authorisation.md).

---

## The `X-Factstore-CI-Context` Header

When creating a trail, include the `X-Factstore-CI-Context` request header to tell the Factstore server which CI environment you are running in. The server will read well-known environment variables from its own process (when deployed inside the same CI environment) and automatically populate any missing trail fields.

**Supported values:**

| Header value | CI system |
|---|---|
| `github-actions` | GitHub Actions |
| `gitlab-ci` | GitLab CI/CD |
| `jenkins` | Jenkins |
| `circleci` | CircleCI |
| `azure-devops` | Azure DevOps Pipelines |

**Auto-populated fields:**

| Field | GitHub Actions | GitLab CI | Jenkins | CircleCI | Azure DevOps |
|---|---|---|---|---|---|
| `gitCommitSha` | `GITHUB_SHA` | `CI_COMMIT_SHA` | `GIT_COMMIT` | `CIRCLE_SHA1` | `BUILD_SOURCEVERSION` |
| `gitBranch` | `GITHUB_REF_NAME` | `CI_COMMIT_REF_NAME` | `GIT_BRANCH` | `CIRCLE_BRANCH` | `BUILD_SOURCEBRANCH` |
| `buildUrl` | constructed from `GITHUB_SERVER_URL` + `GITHUB_REPOSITORY` + `GITHUB_RUN_ID` | `CI_JOB_URL` | `BUILD_URL` | `CIRCLE_BUILD_URL` | constructed from `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI` + `SYSTEM_TEAMPROJECT` + `BUILD_BUILDID` |

Fields explicitly provided in the request body always take precedence over auto-populated values.

---

## GitHub Actions

### Using `curl` directly

```yaml
name: Compliance Trail

on: [push]

jobs:
  factstore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Create Factstore trail
        run: |
          curl -s -X POST "${{ vars.FACTSTORE_BASE_URL }}/api/v1/trails" \
            -H "Authorization: Bearer ${{ secrets.FACTSTORE_API_TOKEN }}" \
            -H "Content-Type: application/json" \
            -H "X-Factstore-CI-Context: github-actions" \
            -d '{
              "flowId": "${{ vars.FACTSTORE_FLOW_ID }}",
              "gitAuthor": "${{ github.actor }}",
              "gitAuthorEmail": "${{ github.actor }}@users.noreply.github.com"
            }'
```

Because the header `X-Factstore-CI-Context: github-actions` is present, the server reads `GITHUB_SHA`, `GITHUB_REF_NAME`, and constructs the build URL from `GITHUB_SERVER_URL`/`GITHUB_REPOSITORY`/`GITHUB_RUN_ID` automatically.

### Using the `setup-factstore` action

The `actions/setup-factstore` action (defined in this repository at [`actions/setup-factstore/action.yml`](../actions/setup-factstore/action.yml)) configures the Factstore CLI for use in subsequent steps.

```yaml
name: Compliance Trail

on: [push]

jobs:
  factstore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Factstore
        uses: ./actions/setup-factstore
        with:
          base-url: ${{ vars.FACTSTORE_BASE_URL }}
          api-token: ${{ secrets.FACTSTORE_API_TOKEN }}

      - name: Create trail
        run: |
          # CLI usage will be available once issue #33 is delivered.
          # For now, use curl with the X-Factstore-CI-Context header (see above).
          echo "Trail created for commit $GITHUB_SHA on branch $GITHUB_REF_NAME"
```

---

---

## Evidence from several pipelines on one trail

A Trail represents one release. In most organisations the gates for that release do not all run
in the same pipeline: the primary CI/CD pipeline owns build provenance, unit tests, SAST/GHAS and
image scanning, while **integration tests, API tests and environment-specific testing** run from
separate pipelines owned by other teams.

All of that evidence belongs on **one** trail, otherwise there is no end-to-end compliance view for
the release. The mechanism is a **release identifier** (`externalId`): a stable string that every
pipeline working on the same release already knows.

### 1. The primary pipeline creates the trail with a release identifier

Trail creation is **idempotent** on `(flowId, externalId)`. Posting the same release identifier
twice returns the existing trail (`200`, `status: "exists"`) instead of creating a second one
(`201`, `status: "created"`), so a re-run of the primary pipeline cannot fork the evidence for a
release.

```yaml
      - name: Create (or join) the release trail
        run: |
          factstore trails create \
            --flow-id "${{ vars.FACTSTORE_FLOW_ID }}" \
            --external-id "${{ github.repository }}@${{ github.run_id }}" \
            --commit "${{ github.sha }}" \
            --branch "${{ github.ref_name }}" \
            --author "${{ github.actor }}" \
            --author-email "${{ github.actor }}@users.noreply.github.com"
```

Pick an identifier that is stable for the release and known to every pipeline — a run id, a build
number, a release tag, or `repo@version`. Do **not** use the commit SHA alone if a commit can be
released more than once.

### 2. Gates that run before the image exists still count

Unit tests and SAST finish long before a container image is pushed, so there is no digest to key
them on. Attest them against the trail instead:

```yaml
      - name: Attest unit tests
        run: |
          factstore attest junit \
            --flow-id "${{ vars.FACTSTORE_FLOW_ID }}" \
            --trail-external-id "${{ github.repository }}@${{ github.run_id }}" \
            --test-results-file build/test-results/test/TEST-*.xml
```

### 3. A downstream pipeline resolves the same trail by that identifier

The secondary pipeline needs no UUID. Pass it the release identifier as a workflow input and let
it resolve the trail:

```yaml
name: Integration Tests

on:
  workflow_call:
    inputs:
      release-id:
        required: true
        type: string

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: ./actions/setup-factstore
        with:
          base-url: ${{ vars.FACTSTORE_BASE_URL }}
          api-token: ${{ secrets.FACTSTORE_API_TOKEN }}

      - name: Run integration tests
        id: tests
        run: ./gradlew integrationTest

      - name: Attest integration tests to the release trail
        if: always()
        run: |
          factstore attest generic \
            --flow-id "${{ vars.FACTSTORE_FLOW_ID }}" \
            --trail-external-id "${{ inputs.release-id }}" \
            --type integration-tests \
            --name integration-tests \
            --status ${{ steps.tests.outcome == 'success' && 'PASSED' || 'FAILED' }} \
            --evidence-url "${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

The primary pipeline invokes it with the identifier it used:

```yaml
  integration-tests:
    needs: [build]
    uses: ./.github/workflows/integration-tests.yml
    with:
      release-id: ${{ github.repository }}@${{ github.run_id }}
    secrets: inherit
```

`factstore trails lookup` resolves a trail on its own if a job needs the UUID for something else:

```bash
factstore trails lookup --flow-id "$FLOW_ID" --external-id "$RELEASE_ID" --json
factstore trails lookup --flow-id "$FLOW_ID" --name nightly-release
factstore trails lookup --flow-id "$FLOW_ID" --commit "$GITHUB_SHA"   # most recent run
```

### 4. The release gate waits for every pipeline

Define the flow to require the gates from *all* the pipelines:

```yaml
requiredAttestationTypes:
  - unit-tests          # primary pipeline
  - ghas                # primary pipeline
  - image-scan          # primary pipeline
  - integration-tests   # integration pipeline
  - api-tests           # API test pipeline
```

The trail stays `PENDING` until the downstream pipelines have reported, which is exactly the
behaviour you want — a release cannot pass its gate on the primary pipeline's evidence alone.

The gate itself asserts the **trail**, never a bare digest, so it is judged on the evidence for
*this* release:

```yaml
      - name: Release gate
        run: |
          factstore assert \
            --flow-id "${{ vars.FACTSTORE_FLOW_ID }}" \
            --trail-id "$(factstore trails lookup \
                --flow-id "${{ vars.FACTSTORE_FLOW_ID }}" \
                --external-id "${{ github.repository }}@${{ github.run_id }}" \
                --json | jq -r .id)"
```

> Asserting on a digest alone is judged against the most recent trail carrying that digest. Always
> scope a CI gate to its own trail — see [API_REFERENCE.md](./API_REFERENCE.md#compliance-assertion).

### Ordering

Pipelines may run in any order and attest concurrently. Whichever pipeline reaches
`trails create` first creates the trail; the others join it. If a downstream pipeline can start
before the primary one, give it the same `factstore trails create --external-id` call rather than
`lookup`, so it creates the trail if it is first and joins it if it is not.

---

## GitLab CI/CD

Add a job to your `.gitlab-ci.yml` that uses `curl` with the `X-Factstore-CI-Context: gitlab-ci` header. GitLab exposes `CI_COMMIT_SHA`, `CI_COMMIT_REF_NAME`, and `CI_JOB_URL` automatically.

```yaml
factstore-trail:
  stage: .pre
  image: curlimages/curl:latest
  script:
    - |
      curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
        -H "Content-Type: application/json" \
        -H "X-Factstore-CI-Context: gitlab-ci" \
        -d "{
          \"flowId\": \"$FACTSTORE_FLOW_ID\",
          \"gitAuthor\": \"$GITLAB_USER_LOGIN\",
          \"gitAuthorEmail\": \"$GITLAB_USER_EMAIL\"
        }"
  variables:
    FACTSTORE_BASE_URL: https://factstore.example.com
    FACTSTORE_FLOW_ID: your-flow-uuid-here
```

Set `FACTSTORE_BASE_URL` and `FACTSTORE_FLOW_ID` as CI/CD variables in your GitLab project settings. Store any API token as a masked variable.

---

## Jenkins

Add a `sh` step in your `Jenkinsfile`. Jenkins populates `GIT_COMMIT`, `GIT_BRANCH`, and `BUILD_URL` in the build environment.

```groovy
pipeline {
    agent any

    environment {
        FACTSTORE_BASE_URL = 'https://factstore.example.com'
        FACTSTORE_FLOW_ID  = 'your-flow-uuid-here'
    }

    stages {
        stage('Factstore Trail') {
            steps {
                sh '''
                    curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
                      -H "Content-Type: application/json" \
                      -H "X-Factstore-CI-Context: jenkins" \
                      -d "{
                        \\"flowId\\": \\"$FACTSTORE_FLOW_ID\\",
                        \\"gitAuthor\\": \\"$BUILD_USER\\",
                        \\"gitAuthorEmail\\": \\"$BUILD_USER_EMAIL\\"
                      }"
                '''
            }
        }
    }
}
```

Install the [Build User Vars Plugin](https://plugins.jenkins.io/build-user-vars-plugin/) to expose `BUILD_USER` and `BUILD_USER_EMAIL`.

---

## CircleCI

Add a run step to your `.circleci/config.yml`. CircleCI exposes `CIRCLE_SHA1`, `CIRCLE_BRANCH`, and `CIRCLE_BUILD_URL`.

```yaml
version: 2.1

jobs:
  factstore-trail:
    docker:
      - image: cimg/base:stable
    steps:
      - run:
          name: Create Factstore trail
          command: |
            curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
              -H "Content-Type: application/json" \
              -H "X-Factstore-CI-Context: circleci" \
              -d "{
                \"flowId\": \"$FACTSTORE_FLOW_ID\",
                \"gitAuthor\": \"$CIRCLE_USERNAME\",
                \"gitAuthorEmail\": \"$CIRCLE_USERNAME@users.noreply.github.com\"
              }"

workflows:
  compliance:
    jobs:
      - factstore-trail
```

Set `FACTSTORE_BASE_URL` and `FACTSTORE_FLOW_ID` as [environment variables](https://circleci.com/docs/env-vars/) in your CircleCI project or organization settings.

---

## Azure DevOps

Add a script task to your `azure-pipelines.yml`. Azure DevOps exposes `BUILD_SOURCEVERSION`, `BUILD_SOURCEBRANCH`, and the build URL can be constructed from `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI`, `SYSTEM_TEAMPROJECT`, and `BUILD_BUILDID`.

```yaml
trigger:
  - main

pool:
  vmImage: ubuntu-latest

variables:
  FACTSTORE_BASE_URL: https://factstore.example.com
  FACTSTORE_FLOW_ID: your-flow-uuid-here

steps:
  - task: Bash@3
    displayName: Create Factstore trail
    inputs:
      targetType: inline
      script: |
        curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
          -H "Content-Type: application/json" \
          -H "X-Factstore-CI-Context: azure-devops" \
          -d "{
            \"flowId\": \"$FACTSTORE_FLOW_ID\",
            \"gitAuthor\": \"$(Build.RequestedFor)\",
            \"gitAuthorEmail\": \"$(Build.RequestedForEmail)\"
          }"
```

Store `FACTSTORE_BASE_URL` and any API token as [pipeline variables or variable groups](https://learn.microsoft.com/en-us/azure/devops/pipelines/library/variable-groups) in Azure DevOps Library. Mark secrets as secret variables.

---

## Environment Variable Reference

Full mapping of CI environment variables used by the `X-Factstore-CI-Context` auto-population feature:

| Field | GitHub Actions | GitLab CI | Jenkins | CircleCI | Azure DevOps |
|---|---|---|---|---|---|
| `gitCommitSha` | `GITHUB_SHA` | `CI_COMMIT_SHA` | `GIT_COMMIT` | `CIRCLE_SHA1` | `BUILD_SOURCEVERSION` |
| `gitBranch` | `GITHUB_REF_NAME` | `CI_COMMIT_REF_NAME` | `GIT_BRANCH` | `CIRCLE_BRANCH` | `BUILD_SOURCEBRANCH` |
| `buildUrl` | `GITHUB_SERVER_URL` + `GITHUB_REPOSITORY` + `/actions/runs/` + `GITHUB_RUN_ID` | `CI_JOB_URL` | `BUILD_URL` | `CIRCLE_BUILD_URL` | `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI` + `SYSTEM_TEAMPROJECT` + `/_build/results?buildId=` + `BUILD_BUILDID` |
