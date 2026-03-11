# 17 — Continuous Evidence Collection Pipeline

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Implement an automated pipeline for continuous evidence collection that captures security scan results, test coverage metrics, compliance check outcomes, and build metadata from every code commit. Evidence is automatically correlated with the appropriate trail and stored in the fact store without manual intervention.

## Motivation

From the financial services compliance scenario:

> *"Every code commit automatically logs security scan results, test coverage, and compliance checks into the fact store."*

While the CI/CD Pipeline Webhooks feature (Issue #10) handles inbound event reception, this feature focuses on the **end-to-end automation** of evidence collection — ensuring that every commit in the pipeline automatically produces, collects, and stores all required compliance evidence.

## Requirements

### Evidence Collection Agents

Lightweight CLI tools or container images that run in CI/CD pipelines to collect and report evidence:

#### `factstore-agent` CLI

```bash
# Report test coverage
factstore-agent report-coverage \
    --trail-id $TRAIL_ID \
    --tool junit \
    --coverage-file coverage.xml \
    --min-coverage 80

# Report security scan
factstore-agent report-scan \
    --trail-id $TRAIL_ID \
    --tool owasp-zap \
    --report-file zap-report.json

# Report build provenance
factstore-agent report-build \
    --trail-id $TRAIL_ID \
    --artifact-sha $IMAGE_SHA \
    --source-commit $GIT_SHA \
    --builder github-actions

# Check compliance status
factstore-agent check-compliance \
    --trail-id $TRAIL_ID \
    --flow-id $FLOW_ID
```

### Automated Evidence Types

| Evidence Type | Source | Trigger |
|---|---|---|
| Test Coverage | JUnit/JaCoCo/Istanbul reports | After test execution |
| Security Scan | OWASP ZAP/Snyk/Trivy reports | After security scan |
| Build Provenance | CI/CD system metadata | After build completion |
| Code Review | GitHub/GitLab PR metadata | After PR merge |
| Dependency Audit | npm audit/pip audit reports | After dependency install |
| License Compliance | License scanner reports | After dependency install |
| Container Scan | Container image scan reports | After image build |

### Pipeline Templates

Provide ready-to-use CI/CD pipeline templates:

#### GitHub Actions Workflow Template

```yaml
name: Compliance Pipeline
on:
  push:
    branches: [main]

jobs:
  compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Create Trail
        run: |
          TRAIL_ID=$(curl -s -X POST http://factstore/api/v1/trails \
            -H "Content-Type: application/json" \
            -d '{"flowId": "$FLOW_ID", "gitCommit": "${{ github.sha }}"}' \
            | jq -r '.id')
          echo "TRAIL_ID=$TRAIL_ID" >> $GITHUB_ENV

      - name: Run Tests
        run: ./gradlew test jacocoTestReport

      - name: Report Coverage
        run: |
          factstore-agent report-coverage \
            --trail-id $TRAIL_ID \
            --tool jacoco \
            --coverage-file build/reports/jacoco/test/jacocoTestReport.xml

      - name: Run Security Scan
        run: |
          docker run --rm owasp/zap2docker-stable zap-baseline.py \
            -t https://app.company.com -J zap-report.json

      - name: Report Security Scan
        run: |
          factstore-agent report-scan \
            --trail-id $TRAIL_ID \
            --tool owasp-zap \
            --report-file zap-report.json

      - name: Check Compliance
        run: |
          factstore-agent check-compliance \
            --trail-id $TRAIL_ID \
            --flow-id $FLOW_ID
```

### Evidence Correlation

- Automatically correlate evidence to trails by git commit SHA
- Support for multi-artifact trails (monorepo support)
- Evidence deduplication (same evidence from multiple pipeline runs)

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/evidence/collect` | Bulk evidence collection endpoint |
| POST | `/api/v1/trails/{trailId}/coverage` | Report test coverage |
| GET | `/api/v1/trails/{trailId}/coverage` | Get test coverage results |
| GET | `/api/v1/trails/{trailId}/evidence-summary` | Summary of all collected evidence |
| GET | `/api/v1/evidence/gaps` | Identify missing evidence across trails |

### Frontend

- Evidence collection status on Trail Detail view
- Evidence completeness indicator (all required evidence collected?)
- Missing evidence alerts
- Pipeline integration documentation page

## Acceptance Criteria

- [ ] `factstore-agent` CLI specification documented
- [ ] Bulk evidence collection API endpoint implemented
- [ ] Test coverage recording and retrieval endpoints
- [ ] Evidence summary endpoint per trail
- [ ] Evidence gap detection endpoint
- [ ] GitHub Actions workflow template created
- [ ] Evidence correlation by git commit SHA
- [ ] Evidence deduplication logic
- [ ] Unit tests for evidence collection and correlation
- [ ] Frontend evidence summary display
- [ ] OpenAPI documentation updated

## Technical Notes

- The `factstore-agent` CLI can initially be a shell script wrapper around `curl` commands
- Future iterations can implement a Go or Kotlin CLI binary
- Evidence gap detection uses flow attestation requirements vs collected evidence
