# GitLab CI Integration

Factstore provides a reusable GitLab CI template (`gitlab/factstore.yml`) that you can include in your pipeline to record attestations and enforce deployment gates.

## Prerequisites

- A running Factstore instance accessible from your GitLab runners
- A Factstore API token stored as a CI/CD variable (e.g. `FACTSTORE_API_TOKEN`, masked and protected)

## Including the Template

Add the following to your `.gitlab-ci.yml`:

```yaml
include:
  - project: your-org/factstore
    file: gitlab/factstore.yml
```

Or, if Factstore is hosted externally, use a remote include:

```yaml
include:
  - remote: 'https://raw.githubusercontent.com/MaximumTrainer/OpenFactstore/main/gitlab/factstore.yml'
```

---

## Jobs

### `.factstore-attest` — Record an Attestation

Extend this hidden job to record evidence that a pipeline stage completed.

**Required CI/CD variables:**

| Variable | Description |
|---|---|
| `FACTSTORE_URL` | Base URL of your Factstore instance |
| `FACTSTORE_API_TOKEN` | Bearer token for authentication |
| `FACTSTORE_TRAIL_ID` | Trail ID (UUID) to attach the attestation to |
| `FACTSTORE_ATTESTATION_TYPE` | Attestation type, e.g. `unit-test`, `security-scan` |

**Optional variables:**

| Variable | Default | Description |
|---|---|---|
| `FACTSTORE_ATTESTATION_STATUS` | `PASSED` | `PASSED` or `FAILED` |
| `FACTSTORE_ATTESTATION_DETAILS` | `""` | Free-text details |

**Example:**

```yaml
attest-unit-test:
  extends: .factstore-attest
  stage: attest
  needs: [unit-test]
  variables:
    FACTSTORE_ATTESTATION_TYPE: unit-test
    FACTSTORE_ATTESTATION_STATUS: PASSED
```

---

### `.factstore-gate` — Evaluate a Deployment Gate

Extend this hidden job to block deployment if an artifact does not meet policy requirements.

**Required CI/CD variables:**

| Variable | Description |
|---|---|
| `FACTSTORE_URL` | Base URL of your Factstore instance |
| `FACTSTORE_API_TOKEN` | Bearer token for authentication |
| `FACTSTORE_ARTIFACT_SHA256` | SHA-256 digest of the artifact to evaluate |

**Optional variables:**

| Variable | Default | Description |
|---|---|---|
| `FACTSTORE_ENVIRONMENT` | `""` | Target environment, e.g. `production` |
| `FACTSTORE_FAIL_ON_BLOCK` | `true` | Set to `false` to allow blocked gates without failing the job |

**Example:**

```yaml
gate-production:
  extends: .factstore-gate
  stage: gate
  needs: [attest-unit-test]
  variables:
    FACTSTORE_ENVIRONMENT: production
```

---

## Full Pipeline Example

```yaml
include:
  - project: your-org/factstore
    file: gitlab/factstore.yml

stages:
  - build
  - test
  - attest
  - gate
  - deploy

variables:
  FACTSTORE_URL: https://factstore.your-org.com
  FACTSTORE_TRAIL_ID: $CI_PIPELINE_ID   # use a real UUID in production

build:
  stage: build
  script:
    - docker build -t myapp:$CI_COMMIT_SHA .
    - docker inspect myapp:$CI_COMMIT_SHA --format '{{index .RepoDigests 0}}' > image-digest.txt
  artifacts:
    paths:
      - image-digest.txt

unit-test:
  stage: test
  script:
    - ./gradlew test

attest-unit-test:
  extends: .factstore-attest
  stage: attest
  needs: [unit-test]
  variables:
    FACTSTORE_ATTESTATION_TYPE: unit-test
    FACTSTORE_ATTESTATION_STATUS: PASSED

gate-production:
  extends: .factstore-gate
  stage: gate
  needs: [attest-unit-test]
  before_script:
    - export FACTSTORE_ARTIFACT_SHA256=$(cat image-digest.txt | cut -d: -f2)
  variables:
    FACTSTORE_ENVIRONMENT: production
    FACTSTORE_FAIL_ON_BLOCK: "true"

deploy-production:
  stage: deploy
  needs: [gate-production]
  script:
    - echo "Deploying to production (gate passed)!"
  environment:
    name: production
```

---

## CI/CD Variable Setup

Configure the following in **Settings → CI/CD → Variables**:

| Name | Type | Description |
|---|---|---|
| `FACTSTORE_URL` | Variable | Base URL of your Factstore instance |
| `FACTSTORE_API_TOKEN` | Secret / Masked | API bearer token — mask this value |

Set `FACTSTORE_API_TOKEN` as **masked** and **protected** to prevent it from appearing in job logs.
