# 16 — Security Scan Integration (OWASP)

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Implement first-class support for ingesting and tracking security scan results from OWASP tools (ZAP, Dependency-Check) and other security scanners (Snyk, Trivy, Grype). Security scan results are stored as structured attestations with vulnerability details, enabling automated compliance gates based on vulnerability severity thresholds.

## Motivation

From the financial services compliance scenario:

> *"Every code commit automatically logs security scan results, test coverage, and compliance checks into the fact store."*
>
> Example Fact: *"Commit abc123 passed OWASP security scan with zero critical vulnerabilities."*

Security scanning is a core component of continuous evidence collection. The fact store needs to understand security scan results at a granular level — not just PASS/FAIL but the specific vulnerability counts by severity.

## Requirements

### Security Scan Attestation Schema

Extend the attestation model with a structured `SecurityScanResult` details schema:

```json
{
    "tool": "OWASP ZAP",
    "tool_version": "2.14.0",
    "scan_type": "DAST",
    "target": "https://api.company.com",
    "critical_vulnerabilities": 0,
    "high_vulnerabilities": 2,
    "medium_vulnerabilities": 5,
    "low_vulnerabilities": 10,
    "informational": 15,
    "scan_duration_seconds": 3600,
    "report_url": "vault://secrets/evidence/scan-12345"
}
```

### Supported Security Scanners

| Scanner | Type | Format |
|---|---|---|
| OWASP ZAP | DAST (Dynamic Application Security Testing) | JSON/XML report |
| OWASP Dependency-Check | SCA (Software Composition Analysis) | JSON report |
| Snyk | SCA + SAST | JSON report |
| Trivy | Container vulnerability scanning | JSON report |
| Grype | Container vulnerability scanning | JSON report |
| SonarQube | SAST (Static Application Security Testing) | JSON via API |

### Vulnerability Threshold Policies

Define acceptable vulnerability thresholds per flow:

| Severity | Default Threshold | Action |
|---|---|---|
| Critical | 0 | Block deployment |
| High | 0 | Block deployment |
| Medium | 10 | Warn |
| Low | No limit | Info only |

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/trails/{trailId}/security-scans` | Record a security scan result |
| GET | `/api/v1/trails/{trailId}/security-scans` | List security scans for a trail |
| GET | `/api/v1/security-scans/{id}` | Get security scan details |
| GET | `/api/v1/security-scans/summary` | Aggregate security scan summary |
| POST | `/api/v1/flows/{flowId}/security-thresholds` | Set vulnerability thresholds |
| GET | `/api/v1/flows/{flowId}/security-thresholds` | Get vulnerability thresholds |

### CI/CD Integration

Provide examples and documentation for integrating with CI/CD pipelines:

**GitHub Actions Example:**

```yaml
- name: Run OWASP ZAP Scan
  uses: zaproxy/action-full-scan@v0.7.0
  with:
    target: 'https://api.company.com'

- name: Report to Factstore
  run: |
    curl -X POST http://factstore:8080/api/v1/trails/$TRAIL_ID/security-scans \
      -H "Content-Type: application/json" \
      -d @zap-report.json
```

### Report Parsing

- Dedicated parsers for each scanner output format
- Normalize all scan results into the common `SecurityScanResult` schema
- Handle both JSON and XML report formats

### Frontend

- Security scan results displayed on Trail Detail view
- Vulnerability count badges by severity
- Security scan trend chart (vulnerabilities over time)
- Security threshold configuration on Flow settings page

## Acceptance Criteria

- [ ] SecurityScanResult schema defined and integrated with attestation model
- [ ] OWASP ZAP report parser implemented
- [ ] OWASP Dependency-Check report parser implemented
- [ ] At least one additional scanner parser (Snyk, Trivy, or Grype)
- [ ] Vulnerability threshold policy entity and API
- [ ] Security scan recording and retrieval endpoints
- [ ] Aggregate security summary endpoint
- [ ] Integration with Deployment Gate (thresholds affect gate decisions)
- [ ] CI/CD integration examples documented
- [ ] Unit tests for report parsers and threshold logic
- [ ] Frontend security scan display on Trail Detail
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- JSON/XML parsing libraries (Jackson already available)
- No additional external dependencies required for initial implementation
