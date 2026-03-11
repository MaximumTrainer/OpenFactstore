# 11 — HashiCorp Vault Integration

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Integrate HashiCorp Vault as a secure evidence storage backend for the Fact Store. Evidence artifacts (security scan reports, compliance logs, approval records) are stored in Vault with cryptographic sealing, ensuring tamper-proof storage and fine-grained access control.

## Motivation

Financial services regulations require that compliance evidence be stored securely and be tamper-proof. HashiCorp Vault provides:

- **Secrets management** for API keys, certificates, and sensitive configuration
- **Encryption as a service** for evidence artifacts at rest
- **Access control** with fine-grained policies and audit logging
- **Dynamic secrets** for database credentials and cloud provider tokens

Currently, the Fact Store's evidence vault stores evidence content in the application database (for example, as BLOBs) with no dedicated encryption or fine-grained access control. In a production financial services environment, this is insufficient.

## Requirements

### Vault Integration Layer

- New outbound port: `ISecureEvidenceStore` interface
- Vault adapter implementing `ISecureEvidenceStore`
- Support for Vault KV v2 secrets engine
- Support for Vault Transit secrets engine (encryption as a service)

### Evidence Storage

Store evidence artifacts in Vault with structured paths:

```
vault kv put secret/evidence/software_release/release-v1.2.3/security_scan \
    result="Passed" \
    report_url="s3://company-compliance/reports/release-v1.2.3.pdf"
```

Path structure: `secret/evidence/{entity_type}/{entity_id}/{evidence_type}`

### Configuration

- Vault server URL (environment variable: `VAULT_ADDR`)
- Authentication method support: Token, AppRole, Kubernetes
- TLS configuration for secure communication
- Connection health check endpoint

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/evidence/{entityType}/{entityId}` | Store evidence in Vault |
| GET | `/api/v1/evidence/{entityType}/{entityId}` | Retrieve evidence metadata |
| GET | `/api/v1/evidence/{entityType}/{entityId}/download` | Download evidence artifact |
| DELETE | `/api/v1/evidence/{entityType}/{entityId}` | Soft-delete evidence (mark as archived) |
| GET | `/api/v1/evidence/health` | Vault connectivity health check |

### Security Requirements

- All evidence artifacts encrypted at rest using Vault Transit engine
- Access to evidence requires authentication and authorization
- Audit log of all evidence access events
- No plaintext secrets in application configuration or logs

### Frontend

- Evidence Vault page shows evidence stored in Vault
- Download links for evidence artifacts with temporary signed URLs
- Vault connectivity status indicator in admin panel

## Acceptance Criteria

- [ ] `ISecureEvidenceStore` port interface defined
- [ ] Vault KV v2 adapter implemented
- [ ] Evidence storage and retrieval API endpoints implemented
- [ ] Vault authentication (Token and AppRole) implemented
- [ ] TLS configuration for Vault communication
- [ ] Health check endpoint for Vault connectivity
- [ ] Evidence access audit logging
- [ ] Unit tests with mock Vault server
- [ ] Docker Compose updated with Vault dev server
- [ ] Frontend Evidence Vault page updated
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- `org.springframework.vault:spring-vault-core` (Spring Vault integration)
- HashiCorp Vault dev server for local development

### Configuration Example

```yaml
vault:
  uri: ${VAULT_ADDR:http://localhost:8200}
  authentication: TOKEN
  token: ${VAULT_TOKEN}
  kv:
    enabled: true
    backend: secret
    default-context: factstore
```
