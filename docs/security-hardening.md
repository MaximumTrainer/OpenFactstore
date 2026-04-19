# OpenFactstore — Security Hardening Checklist

Financial institution context: SOC2 Type II / ISO 27001 / PCI-DSS v4 / GDPR Article 32.

Use this checklist during initial deployment, periodic security reviews, and before any compliance audit.

---

## 1. Authentication & Access Control

- [ ] **Enable API authentication**  
  Set `SECURITY_ENFORCE_AUTH=true` in production. Every API request must carry a valid bearer token.

- [ ] **Use service accounts for CI/CD**  
  Never use personal API keys in automated pipelines. Create a dedicated service account per pipeline via `POST /api/v1/service-accounts`.

- [ ] **Rotate API keys regularly**  
  Implement a key rotation policy (e.g., every 90 days for SOC2). Use the zero-downtime rotation procedure: create new key → update callers → delete old key.

- [ ] **Limit API key scope**  
  Issue one API key per CI/CD pipeline or service. Do not share keys across teams or projects.

- [ ] **Restrict Swagger UI in production**  
  Disable or IP-restrict the Swagger UI endpoint (`/swagger-ui.html`) to prevent unauthenticated API exploration. Configure via reverse proxy or network policy.

- [ ] **Apply least-privilege database permissions**  
  The application database user should have `SELECT`, `INSERT`, `UPDATE` only. Flyway migrations should run under a separate privileged user at deploy time only.

---

## 2. Network Security

- [ ] **Enforce TLS 1.2+ on all connections**  
  Terminate TLS at the load balancer / ingress. Redirect all HTTP traffic to HTTPS. Reject TLS < 1.2.

- [ ] **Use private networking for database connections**  
  Place PostgreSQL in a private subnet with no public endpoint. Connect via private link or security group rules that allow only the application tier.

- [ ] **Network-segment the Factstore service**  
  Deploy in a dedicated namespace/VPC with ingress limited to the CI/CD network and authorised consumers. Block all other inbound traffic.

- [ ] **Enable `ssl=require` on the JDBC connection string for PostgreSQL**  
  Add `?ssl=true&sslmode=require` (or `verify-full` with a certificate) to `spring.datasource.url`.

- [ ] **Restrict Actuator endpoints**  
  Expose only `/actuator/health` and `/actuator/prometheus` externally. Block `/actuator/env`, `/actuator/beans`, and others via network policy or Spring Security configuration.

---

## 3. Secret Management

- [ ] **Never hard-code secrets**  
  All credentials must come from environment variables, a secrets manager, or a vault — never from source code or container image layers.

- [ ] **Replace all default secret values**  
  - `AUDIT_HMAC_SECRET` — must not be `change-me-in-production`
  - `SSO_JWT_SECRET` — must not be `changeme-in-production`
  - `SCM_ENCRYPTION_KEY` — must not be `default-dev-key-32chars!!!!!!`
  - `GF_SECURITY_ADMIN_PASSWORD` (Grafana) — must not be `changeme`

- [ ] **Enable HashiCorp Vault integration**  
  Set `VAULT_ENABLED=true` and configure AppRole authentication (`VAULT_ROLE_ID` / `VAULT_SECRET_ID`) instead of a root token. You **must** also set `VAULT_AUTH_METHOD=APPROLE` (matching `vault.authentication=APPROLE` in Spring config), otherwise the application will continue to use token auth and ignore the AppRole fields. Store all application secrets in Vault KV.

- [ ] **Use short-lived Vault tokens**  
  Configure Vault policies with the minimum required capabilities (`read` on specific paths). Set token TTL ≤ 1 hour with renewal.

- [ ] **Store CI/CD secrets in GitHub Secrets (or equivalent)**  
  Use `${{ secrets.FACTSTORE_API_KEY }}` in workflows. Never log secret values — use the `env:` + `jq` pattern to avoid direct interpolation in shell commands.

---

## 4. Data Protection

- [ ] **Enable database encryption at rest**  
  Use AES-256 at the managed database layer (AWS RDS, Azure Database for PostgreSQL, Google Cloud SQL).

- [ ] **Enable PostgreSQL SSL for data in transit**  
  See Network Security section above.

- [ ] **Configure automated database backups**  
  Minimum 7-day retention with point-in-time recovery (PITR) enabled. Test restores quarterly.

- [ ] **Enable the immutable audit ledger (for regulated workloads)**  
  Set `LEDGER_ENABLED=true` and `LEDGER_TYPE=qldb` (AWS) to obtain a cryptographically tamper-evident, durable audit chain. The current `LEDGER_TYPE=local` implementation is in-memory only (non-persistent, lost on restart) and does **not** provide a durable or compliance-grade immutable ledger; use it only for development or low-risk testing in air-gapped environments.

- [ ] **Verify the AUDIT_HMAC_SECRET is a cryptographically random value**  
  Generate with: `openssl rand -hex 32`

---

## 5. Container & Runtime Hardening

- [ ] **Run containers as a non-root user**  
  The base image (`eclipse-temurin:21-jre-alpine`) should run with a dedicated UID. Add `USER 1001` to the Dockerfile or enforce via Kubernetes `securityContext.runAsNonRoot: true`.

- [ ] **Set read-only root filesystem**  
  Apply `securityContext.readOnlyRootFilesystem: true` in the Kubernetes Pod spec. Mount `/tmp` as an `emptyDir` if the JVM requires it.

- [ ] **Drop all Linux capabilities**  
  ```yaml
  securityContext:
    capabilities:
      drop: ["ALL"]
  ```

- [ ] **Do not expose the Vault dev server in production**  
  The `docker-compose.yml` includes a Vault dev server (`VAULT_DEV_ROOT_TOKEN_ID`). This is for local development only. Production must use a production-mode Vault cluster.

- [ ] **Scan container images before deployment**  
  Integrate Trivy or Grype into the CI pipeline. Block deployments with CRITICAL severity vulnerabilities.

- [ ] **Pin base image digests**  
  Replace `FROM eclipse-temurin:21-jre-alpine` with `FROM eclipse-temurin:21-jre-alpine@sha256:<digest>` to prevent supply chain attacks via mutable tags.

---

## 6. Audit Logging & Monitoring

- [ ] **Verify the audit log is active**  
  Each mutating API call should produce a corresponding entry retrievable via `GET /api/v1/trails/{id}/audit`. Test after deployment.

- [ ] **Ship logs to a centralised SIEM**  
  Forward application logs (stdout/stderr) and audit events to Splunk, Elastic SIEM, or equivalent. Set a minimum retention of 1 year (SOC2/PCI-DSS requirement).

- [ ] **Configure Prometheus alerts**  
  Set up alerts for:
  - High 5xx error rates (`> 1%` over 5 minutes)
  - Unusual authentication failure spikes
  - Database connection pool saturation

- [ ] **Review Grafana dashboards post-deployment**  
  The included dashboards cover Audit Forensics, Compliance Overview, Deployment Gates, and Security Scans. Verify data is flowing from Prometheus.

- [ ] **Enable access logging at the load balancer / ingress**  
  All inbound HTTP requests, including client IP, response code, and latency, must be logged.

---

## 7. Compliance & Governance

- [ ] **Define and document Flow attestation requirements**  
  Every pipeline that uses Factstore must have a Flow with explicit `requiredAttestationTypes` covering your regulatory obligations (unit-tests, security-scan, compliance-check, etc.).

- [ ] **Enable OPA policy enforcement**  
  Configure `OPA_MODE=embedded` or `external` and load financial-institution-specific Rego policies to enforce deployment gates automatically.

- [ ] **Enable Cosign artifact signature verification (for PCI-DSS / SLSA)**  
  Set `COSIGN_ENABLED=true` and require all container artifacts to be signed before they are accepted by the gate.

- [ ] **Review hub templates for your regulatory framework**  
  Pre-built templates are available under `backend/src/main/resources/hub-templates/`:
  - `pci-dss-v4.yml` — PCI-DSS v4 controls
  - `sox-itgc.yml` — SOX IT General Controls
  - `gdpr-art32.yml` — GDPR Article 32 technical measures
  - `slsa-level-2.yml` / `slsa-level-3.yml` — SLSA supply chain levels

- [ ] **Schedule quarterly access reviews**  
  Review all service accounts and API keys via `GET /api/v1/service-accounts`. Revoke any that are unused.

- [ ] **Run the persona-based verification workflow regularly**  
  The `.github/workflows/verify-factstore.yml` workflow exercises the full fact store / retrieve lifecycle across four personas. Run it on a schedule (`cron`) or after any infrastructure change.

---

## 8. CI/CD Integration Security

- [ ] **Pin all GitHub Actions to a full commit SHA**  
  Replace `uses: actions/checkout@v4` with `uses: actions/checkout@<sha>` to prevent supply chain attacks via mutable tags.

- [ ] **Declare minimal `permissions:` in every workflow job**  
  Use `permissions: {}` for jobs that do not need to write to the repository or read packages.

- [ ] **Never interpolate GitHub context values directly in `run:` scripts**  
  Pass values via `env:` blocks and reference them as shell variables. Use `jq --arg` to build JSON payloads safely.

- [ ] **Use OIDC for cloud authentication**  
  Configure `id-token: write` permission and use `actions/configure-aws-credentials` or equivalent with OIDC to avoid long-lived cloud credentials in GitHub Secrets.

- [ ] **Audit workflow changes**  
  Protect `.github/workflows/` with a CODEOWNERS rule requiring security team review for any workflow modification.
