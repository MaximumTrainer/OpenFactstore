---
title: "Feature: Authentication & Service Accounts"
labels: ["enhancement", "kosli-feature", "security", "authentication"]
---

## Summary

Implement API key-based authentication for Factstore. Introduce **service accounts** (machine identities for CI/CD pipelines) and **personal API keys** (for human operators and local development). All API endpoints are currently unauthenticated — this is unsuitable for any shared or production deployment.

This mirrors Kosli's _Service Accounts_ feature: _"a service account represents a machine user designed for interactions with Kosli from external systems, such as CI or runtime environments"_.

## Motivation

Factstore currently has no access control. Any caller with network access to the server can read or write any data. In a real organisation this means:

- Any CI pipeline can overwrite attestations for any other team's artifacts
- There is no audit trail of _who_ recorded an attestation — only _what_ was recorded
- There is no way to rotate credentials if a key is compromised
- Regulated environments require proof that only authorised systems can write compliance records

## Requirements

### Data Model

- **ServiceAccount** entity:
  - `id` (UUID)
  - `name` — unique, human-readable (e.g. `backend-ci-runner`)
  - `description`
  - `createdAt`, `updatedAt`

- **ApiKey** entity:
  - `id` (UUID)
  - `ownerType`: `SERVICE_ACCOUNT | USER`
  - `ownerId` (UUID — references ServiceAccount)
  - `keyHash` — PBKDF2 or bcrypt hash of the raw key; raw key is **never stored**
  - `label` — descriptive name (e.g. `"GitHub Actions — prod"`)
  - `ttlDays` — optional expiry; `null` means no expiry
  - `expiresAt` (computed from `createdAt + ttlDays`, nullable)
  - `lastUsedAt` (updated on successful auth, nullable)
  - `isActive` boolean
  - `createdAt`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/service-accounts` | Create a new service account |
| GET | `/api/v1/service-accounts` | List all service accounts |
| GET | `/api/v1/service-accounts/{id}` | Get service account details |
| DELETE | `/api/v1/service-accounts/{id}` | Delete a service account (and all its keys) |
| POST | `/api/v1/service-accounts/{id}/api-keys` | Generate a new API key for a service account |
| GET | `/api/v1/service-accounts/{id}/api-keys` | List API keys (metadata only — no raw key) |
| DELETE | `/api/v1/service-accounts/{id}/api-keys/{keyId}` | Revoke an API key |

### Authentication Mechanism

- All protected endpoints require a bearer token in the `Authorization` header:
  ```
  Authorization: Bearer <raw-api-key>
  ```
- The server hashes the incoming token and compares it against stored key hashes
- Expired keys (`expiresAt < now`) must be rejected with HTTP 401
- Revoked keys (`isActive = false`) must be rejected with HTTP 401
- Successful authentication updates `lastUsedAt` on the ApiKey record

### Key Generation

- Raw API keys are generated as cryptographically secure random strings (minimum 32 bytes, base64url-encoded)
- The raw key is returned **only once** at creation time — it is never retrievable again
- Only the PBKDF2/bcrypt hash is persisted

### API Key Rotation (zero-downtime)

Support zero-downtime rotation by allowing multiple active keys per service account simultaneously:

1. Operator creates a new key → receives raw key
2. Operator updates all callers to use the new key
3. Operator revokes the old key via `DELETE /api/v1/service-accounts/{id}/api-keys/{keyId}`

### Environment Variable Support

To align with future CLI support ([Issue #33](https://github.com/MaximumTrainer/Factstore/issues/33)), the server and any client tooling should support:

```bash
export FACTSTORE_API_TOKEN=<raw-api-key>
```

### Spring Security Configuration

- Implement a custom `AuthenticationFilter` that reads the `Authorization: Bearer` header
- Configure Spring Security to protect all `/api/v1/**` endpoints
- Permit unauthenticated access to `/swagger-ui/**`, `/v3/api-docs/**`, and `/actuator/health`
- Return HTTP 401 with a JSON error body (not a redirect) when authentication fails:
  ```json
  { "error": "Unauthorized", "message": "Valid API key required" }
  ```

### OpenAPI / Swagger Integration

- Register a `SecurityScheme` of type `http` with scheme `bearer` in the OpenAPI config
- Annotate all protected endpoints with `@SecurityRequirement(name = "bearerAuth")`
- The Swagger UI should show an "Authorize" button allowing users to enter their API key

## Acceptance Criteria

- [ ] `ServiceAccount` entity is created with full CRUD API (`POST`, `GET`, `DELETE`)
- [ ] `ApiKey` entity stores only hashed keys; raw key is returned once at creation and never again
- [ ] API key generation uses a cryptographically secure random source (min 32 bytes)
- [ ] All `/api/v1/**` endpoints require a valid bearer token (HTTP 401 without one)
- [ ] `/swagger-ui/**`, `/v3/api-docs/**`, and `/actuator/health` remain unauthenticated
- [ ] Expired API keys are rejected with HTTP 401
- [ ] Revoked (inactive) API keys are rejected with HTTP 401
- [ ] Successful authentication updates `lastUsedAt` on the `ApiKey` record
- [ ] Multiple active API keys per service account are supported (for zero-downtime rotation)
- [ ] `DELETE /api/v1/service-accounts/{id}/api-keys/{keyId}` correctly revokes a key
- [ ] Spring Security is configured with a custom bearer-token `AuthenticationFilter`
- [ ] OpenAPI spec includes a `bearerAuth` security scheme; Swagger UI shows an "Authorize" button
- [ ] HTTP 401 responses return a consistent JSON error body (not an HTML redirect)
- [ ] Unit tests cover: key hashing, key validation, expiry check, revocation check
- [ ] Integration tests cover: authenticated request succeeds, missing token returns 401, expired token returns 401
- [ ] Getting Started documentation ([Part 3: Authentication](../docs/getting-started/03-authentication.md)) is updated with working examples once implemented
- [ ] OpenAPI documentation is updated to reflect authentication requirements
