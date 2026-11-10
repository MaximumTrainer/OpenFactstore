# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` branch (latest) | ✅ Active development |
| Older tagged releases | ⚠️ Best-effort only |

We recommend always running the latest commit on `main` or the latest tagged release.

---

## Reporting a Vulnerability

If you discover a security vulnerability in OpenFactstore, please **do not open a public GitHub issue**.

### Preferred method: GitHub Security Advisory

1. Go to the [Security tab](https://github.com/MaximumTrainer/OpenFactstore/security) of the repository.
2. Click **"Report a vulnerability"**.
3. Fill in the advisory form with:
   - A clear description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested mitigations

We will acknowledge receipt within **48 hours** and aim to provide a fix or mitigation within **14 days** for critical issues.

### Alternative: Email

If you prefer email, send details to the repository maintainers. You can find contact information in the repository profile.

---

## Known Limitations

### Authentication is enforced by default

Since #209, `SECURITY_ENFORCE_AUTH` defaults to `true`: a default deployment refuses unauthenticated
requests. Upgrading from an earlier version is covered in
[docs/upgrading/enforce-auth.md](docs/upgrading/enforce-auth.md), including a warn-mode release to
find every unauthenticated caller first, and the one-line rollback (`SECURITY_ENFORCE_AUTH=false`).

With enforcement on:

- unauthenticated requests get `401` with `WWW-Authenticate: Bearer`;
- every endpoint requires the scope its rule names (`docs/api-authorisation-matrix.md`);
- a credential bound to an organisation sees only that organisation, and a cross-organisation
  read needs the `operator` scope and is audited;
- `/actuator/prometheus` needs `metrics:read`; the rest of `/actuator`, except health and info,
  needs `admin`;
- plaintext HTTP from another machine is refused with `426`;
- Swagger UI, `/api-docs` and the H2 console are no longer served unauthenticated;
- HSTS is set and a wildcard CORS origin is refused outright;
- a first-run **bootstrap admin credential** is created so the deployment is administrable.

A reverse proxy is still good practice for TLS and network isolation, but it is **no longer the
only thing standing between the internet and your data**.

See **[docs/api-authorisation.md](./docs/api-authorisation.md)** and
**[docs/authentication.md](./docs/authentication.md)**.

### SCM tokens stored Base64-encoded (without Vault)

When `VAULT_ENABLED=false`, SCM integration tokens (GitHub, GitLab) are stored Base64-encoded in PostgreSQL. This is encoding, not encryption.

**Mitigation:** Set `VAULT_ENABLED=true` and configure HashiCorp Vault for all environments that store real SCM tokens.

### H2 in-memory database (unit tests only)

Unit tests run against an H2 in-memory database. H2 must never be used in production — use PostgreSQL 16.

---

## Production Hardening Checklist

Use this checklist before deploying OpenFactstore to a production environment.

### Network

- [ ] API is not exposed directly to the public internet.
- [ ] API is placed behind a TLS-terminating reverse proxy (nginx, Caddy, AWS ALB).
- [ ] TLS 1.2+ is enforced; TLS 1.0/1.1 and weak cipher suites are disabled.
- [ ] Firewall rules restrict access to port 8080 to known CI/CD IPs and internal networks.
- [ ] PostgreSQL (5432) and Vault (8200) are not exposed outside the private network.

### Authentication & Authorisation

- [ ] `SECURITY_ENFORCE_AUTH=true` — the application refuses unauthenticated requests itself,
      not only at a proxy.
- [ ] Warn mode was run first, and its log shows no unauthenticated callers remaining.
- [ ] `SSO_JWT_SECRET` is set to a real random secret (≥32 bytes). The application refuses to
      start with a placeholder, but check it is not a value from a shared example.
- [ ] Unique service accounts and API keys are created **per CI pipeline**, each scoped with the
      `CI_PIPELINE` preset rather than `admin`.
- [ ] Every API key has a TTL; `security.api-key.allow-non-expiring` is `false`.
- [ ] Keys are rotated at least every 90 days, using `POST /api/v1/api-keys/{id}/rotate` so the
      overlap window avoids an outage.
- [ ] Keys are bound to an organisation (`orgSlug`) where the deployment is multi-tenant.
- [ ] The bootstrap credential has been used, revoked, and `security.bootstrap.enabled` set to
      `false`.
- [ ] `security.cors.allowed-origins` lists the real origins; it is not `*`.
- [ ] Default Grafana password (`changeme`) has been changed.

### Secrets Management

- [ ] `DB_PASSWORD` is stored in a secrets manager (AWS Secrets Manager, GCP Secret Manager, Vault).
- [ ] `VAULT_TOKEN` is stored in a secrets manager; consider Vault auto-unseal via KMS.
- [ ] SCM tokens are stored in Vault (`VAULT_ENABLED=true`), not plain PostgreSQL.
- [ ] No secrets are committed to source control.

### Database

- [ ] PostgreSQL is running with encryption-at-rest enabled.
- [ ] Regular automated backups are configured and tested.
- [ ] Database user `factstore` has minimum required privileges (no superuser).
- [ ] Flyway migrations are the only mechanism for schema changes.

### Observability

- [ ] Prometheus and Grafana are not exposed to the public internet.
- [ ] Grafana is secured with SSO or strong admin credentials.
- [ ] Alerting is configured for `factstore_assert_noncompliant_total` spikes.
- [ ] Audit log (`GET /api/v1/audit`) is monitored for anomalous access patterns.

### Updates

- [ ] A process exists to track and apply dependency updates (Dependabot or Renovate).
- [ ] Container images are rebuilt regularly to pick up OS patch updates.
- [ ] The Dockerfile base image (`eclipse-temurin:21-jre-alpine`) is pinned by digest in production.
