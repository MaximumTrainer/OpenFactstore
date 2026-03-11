# 12 — OPA (Open Policy Agent) Integration

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Integrate Open Policy Agent (OPA) as an external policy evaluation engine for compliance enforcement. OPA evaluates Rego policies against fact store data to make deployment and compliance decisions, enabling decoupled, auditable policy management.

## Motivation

The existing Deployment Gate & Policy Engine (Issue #7) defines an internal policy engine. OPA integration extends this by providing:

- **Externalized policy definitions** using Rego, a purpose-built policy language
- **Decoupled policy management** — policies can be updated without code changes
- **Industry-standard tooling** — OPA is widely adopted in financial services for compliance enforcement
- **Auditable policy decisions** — every evaluation is logged with input, policy, and result

In the financial services compliance scenario:

> *"OPA validates compliance before deployment."*

```rego
package compliance

# Allow deployment only if security scan has passed
allow {
    input.event_type == "Security Scan"
    input.status == "PASSED"
}

# Enforce regulatory controls (e.g., PCI-DSS compliance)
deny["Release requires PCI-DSS approval"] {
    input.event_type == "Regulatory Approval"
    input.status != "APPROVED"
}
```

## Requirements

### OPA Integration Layer

- New outbound port: `IPolicyEvaluator` interface
- OPA adapter implementing `IPolicyEvaluator`
- Support for both embedded OPA (via Rego library) and external OPA server
- Policy bundle management (upload, version, activate policies)

### Policy Evaluation API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/policies/evaluate` | Evaluate an artifact/release against OPA policies |
| POST | `/api/v1/policies/bundles` | Upload a policy bundle (Rego files) |
| GET | `/api/v1/policies/bundles` | List available policy bundles |
| GET | `/api/v1/policies/bundles/{id}` | Get policy bundle details |
| PUT | `/api/v1/policies/bundles/{id}/activate` | Activate a policy bundle |
| GET | `/api/v1/policies/decisions` | List recent policy decisions (audit trail) |
| GET | `/api/v1/policies/decisions/{id}` | Get decision details with input/output |

### Policy Evaluation Flow

1. CI/CD pipeline records a fact (e.g., security scan result) via webhook or API
2. Deployment gate queries OPA with the fact data as input
3. OPA evaluates the Rego policy against the input
4. Decision (allow/deny) is recorded in the fact store with full audit trail
5. Pipeline proceeds or halts based on the decision

### OPA Policy Check (CI/CD Integration)

```bash
curl -X POST http://opa-server:8181/v1/data/compliance \
    -H "Content-Type: application/json" \
    -d '{
      "input": {
        "event_type": "Security Scan",
        "status": "PASSED"
      }
    }'
```

If the response includes `"allow": true`, the pipeline proceeds. Otherwise, it halts.

### Built-in Policy Templates

Provide starter Rego policies for common financial services scenarios:

- **Security scan gate**: Block deployment if critical vulnerabilities found
- **Approval gate**: Require N approvals from specified roles
- **Evidence completeness**: Require all attestation types to be present
- **Time-based controls**: Block deployments outside approved change windows
- **Regulatory gate**: Require specific compliance certifications

### Configuration

```yaml
opa:
  mode: ${OPA_MODE:embedded}  # embedded | external
  external-url: ${OPA_URL:http://localhost:8181}
  policy-path: ${OPA_POLICY_PATH:policies/}
  decision-logging: true
```

### Frontend

- Policy management page showing active bundles and policies
- Policy decision log with filtering
- Policy editor with Rego syntax highlighting (optional)
- Visual policy evaluation results on Trail Detail page

## Acceptance Criteria

- [ ] `IPolicyEvaluator` port interface defined
- [ ] OPA adapter implemented (embedded mode)
- [ ] External OPA server adapter implemented
- [ ] Policy bundle upload and management API
- [ ] Policy evaluation endpoint with decision logging
- [ ] Built-in Rego policy templates for financial services
- [ ] Integration with existing Deployment Gate (Issue #7)
- [ ] Unit tests with mock OPA evaluator
- [ ] Docker Compose updated with OPA server
- [ ] Frontend policy management page
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- OPA REST API client via Spring `RestTemplate` or `WebClient` (already available)
- OPA Docker image: `openpolicyagent/opa:latest`
