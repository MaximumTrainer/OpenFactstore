# OpenFactstore — Installation Runbook

This runbook walks through installing, configuring, and verifying a production-ready OpenFactstore instance within a financial institution context (SOC2 / ISO 27001 / PCI-DSS).

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Local Quick-Start (Development)](#2-local-quick-start-development)
3. [Production Deployment](#3-production-deployment)
4. [Configuration Reference](#4-configuration-reference)
5. [Service Account & API Key Setup](#5-service-account--api-key-setup)
6. [Smoke-Test Verification](#6-smoke-test-verification)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. Prerequisites

| Component | Minimum version | Notes |
|-----------|----------------|-------|
| Java (Eclipse Temurin) | 21 | Runtime and build |
| Node.js | 20 LTS | Frontend build |
| Docker Engine | 24+ | Container image build and Compose |
| Docker Compose | v2.24+ | Local stack orchestration |
| PostgreSQL | 14+ | Managed DB (AWS RDS / Azure Database for PostgreSQL recommended) |
| HashiCorp Vault | 1.14+ | Secret management (optional but recommended for production) |

---

## 2. Local Quick-Start (Development)

### 2a. From source

```bash
# Clone the repository
git clone https://github.com/MaximumTrainer/OpenFactstore.git
cd OpenFactstore

# Start a local PostgreSQL instance
docker compose up -d postgres

# Build and start the backend
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

The server starts on **http://localhost:8080**.

Verify the API is responding:

```bash
curl http://localhost:8080/api/v1/flows
# Expected: []
```

Open the interactive API explorer: **http://localhost:8080/swagger-ui.html**

### 2b. Full stack via Docker Compose

```bash
# Start all services (postgres, vault, backend, prometheus, grafana)
docker compose up -d

# Follow backend logs
docker compose logs -f backend
```

Wait for:

```
Started FactstoreApplication in X.XXX seconds
```

---

## 3. Production Deployment

### 3a. Build the container image

```bash
docker build -t factstore:$(git describe --tags --always) .
docker tag factstore:$(git describe --tags --always) <your-registry>/factstore:latest
docker push <your-registry>/factstore:latest
```

### 3b. Kubernetes / Helm deployment (recommended)

Create a namespace and the required secrets:

```bash
kubectl create namespace factstore

kubectl create secret generic factstore-db \
  --namespace factstore \
  --from-literal=username='<db-user>' \
  --from-literal=password='<db-password>'

kubectl create secret generic factstore-app \
  --namespace factstore \
  --from-literal=audit-hmac-secret='<strong-random-64-chars>' \
  --from-literal=sso-jwt-secret='<strong-random-64-chars>' \
  --from-literal=scm-encryption-key='<exactly-32-chars>'
```

Also create the Vault credentials secret:

```bash
kubectl create secret generic factstore-vault \
  --namespace factstore \
  --from-literal=role-id='<vault-approle-role-id>' \
  --from-literal=secret-id='<vault-approle-secret-id>'
```

```yaml
# factstore-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: factstore
  namespace: factstore
spec:
  replicas: 2
  selector:
    matchLabels:
      app: factstore
  template:
    metadata:
      labels:
        app: factstore
    spec:
      containers:
        - name: factstore
          image: <your-registry>/factstore:latest
          ports:
            - containerPort: 8080
          env:
            - name: DB_HOST
              value: "<rds-endpoint>"
            - name: DB_PORT
              value: "5432"
            - name: DB_NAME
              value: "factstore"
            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: factstore-db
                  key: username
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: factstore-db
                  key: password
            - name: SECURITY_ENFORCE_AUTH
              value: "true"
            - name: VAULT_ENABLED
              value: "true"
            - name: VAULT_ADDR
              value: "https://vault.internal.example.com"
            - name: VAULT_AUTH_METHOD
              value: "APPROLE"
            - name: VAULT_ROLE_ID
              valueFrom:
                secretKeyRef:
                  name: factstore-vault
                  key: role-id
            - name: VAULT_SECRET_ID
              valueFrom:
                secretKeyRef:
                  name: factstore-vault
                  key: secret-id
            - name: AUDIT_HMAC_SECRET
              valueFrom:
                secretKeyRef:
                  name: factstore-app
                  key: audit-hmac-secret
            - name: SSO_JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: factstore-app
                  key: sso-jwt-secret
            - name: SCM_ENCRYPTION_KEY
              valueFrom:
                secretKeyRef:
                  name: factstore-app
                  key: scm-encryption-key
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "2"
              memory: "1Gi"
```

### 3c. Managed database

Provision a PostgreSQL instance with:

- **Encryption at rest** enabled (AES-256)
- **SSL/TLS** enforced for all connections (`require` or `verify-full`)
- **Automated backups** with ≥ 7-day retention
- **Private subnet** placement (no public endpoint)
- Connection from the application pods via a **security group / private link**

Create the database and user:

```sql
CREATE DATABASE factstore;
CREATE USER factstore WITH ENCRYPTED PASSWORD '<strong-password>';
GRANT ALL PRIVILEGES ON DATABASE factstore TO factstore;
```

Flyway runs all schema migrations automatically on first startup.

### 3d. TLS termination

Terminate TLS at the Ingress / Load Balancer layer. Example with an NGINX Ingress and cert-manager:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: factstore
  namespace: factstore
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
    - hosts:
        - factstore.internal.example.com
      secretName: factstore-tls
  rules:
    - host: factstore.internal.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: factstore
                port:
                  number: 8080
```

---

## 4. Configuration Reference

All settings can be overridden via environment variables.

| Environment variable | Default | Description |
|----------------------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `factstore` | Database name |
| `DB_USERNAME` | *(required)* | Database username |
| `DB_PASSWORD` | *(required)* | Database password |
| `SECURITY_ENFORCE_AUTH` | `false` | Set `true` to require API key on all requests |
| `VAULT_ENABLED` | `false` | Enable HashiCorp Vault integration |
| `VAULT_ADDR` | `http://localhost:8200` | Vault address |
| `VAULT_TOKEN` | — | Vault root/service token |
| `VAULT_ROLE_ID` / `VAULT_SECRET_ID` | — | Vault AppRole credentials |
| `AUDIT_HMAC_SECRET` | `change-me-in-production` | HMAC key for audit log integrity — **must be changed** |
| `SSO_JWT_SECRET` | `changeme-in-production` | JWT signing key — **must be changed** |
| `SCM_ENCRYPTION_KEY` | `default-dev-key-32chars!!!!!!` | Encryption key for SCM tokens — **must be changed, exactly 32 chars** |
| `FACTSTORE_REGION` | `us-east-1` | Deployment region tag on audit events |
| `OPA_MODE` | `embedded` | OPA policy engine mode: `embedded` or `external` |
| `COSIGN_ENABLED` | `false` | Enable Sigstore/Cosign artifact signature verification |
| `LEDGER_ENABLED` | `false` | Enable immutable ledger (AWS QLDB or local file) |

---

## 5. Service Account & API Key Setup

> **Bootstrap note:** When `SECURITY_ENFORCE_AUTH=true` is set, the service account and API key endpoints are also protected. To mint the first key, deploy with `SECURITY_ENFORCE_AUTH=false` (or restrict access at the network level), create the service account and initial API key using the steps below, then re-deploy with `SECURITY_ENFORCE_AUTH=true`. Alternatively, enable GitHub OAuth2 SSO (see `application.yml`) and use an authenticated session to create service accounts via the UI.

### 5a. Create a service account for your CI/CD pipeline

```bash
export BASE_URL=https://factstore.internal.example.com

SA=$(curl -sf -X POST "$BASE_URL/api/v1/service-accounts" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "github-actions-pipeline",
    "description": "Service account for GitHub Actions compliance recording"
  }')

SA_ID=$(echo "$SA" | jq -r '.id')
echo "Service account ID: $SA_ID"
```

### 5b. Create an API key

```bash
KEY=$(curl -sf -X POST "$BASE_URL/api/v1/service-accounts/$SA_ID/api-keys" \
  -H "Content-Type: application/json" \
  -d '{"label": "primary-key"}')

API_KEY=$(echo "$KEY" | jq -r '.plainTextKey')
echo "API Key (save this — shown only once): $API_KEY"
```

Store `API_KEY` in your secret manager (GitHub Secrets, AWS Secrets Manager, HashiCorp Vault).

### 5c. Create the compliance flow

```bash
FLOW=$(curl -sf -X POST "$BASE_URL/api/v1/flows" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "name": "payment-service-ci",
    "description": "Compliance flow for payment service CI/CD pipeline",
    "requiredAttestationTypes": ["unit-tests", "security-scan", "compliance-check"]
  }')

FLOW_ID=$(echo "$FLOW" | jq -r '.id')
echo "Flow ID: $FLOW_ID"
```

Store `FLOW_ID` as a GitHub Actions variable (`vars.FLOW_ID`).

---

## 6. Smoke-Test Verification

Run these commands against your deployed instance to verify end-to-end functionality:

```bash
export BASE_URL=https://factstore.internal.example.com
export API_KEY=<your-api-key>
export FLOW_ID=<your-flow-uuid>

# 1. Health check
curl -sf "$BASE_URL/actuator/health" | jq .

# 2. Create a trail
TRAIL=$(curl -sf -X POST "$BASE_URL/api/v1/trails" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d "{\"flowId\": \"$FLOW_ID\", \"gitCommitSha\": \"abc123\", \"gitBranch\": \"main\", \"gitAuthor\": \"smoke-test\"}")
TRAIL_ID=$(echo "$TRAIL" | jq -r '.id')
echo "Trail: $TRAIL_ID"

# 3. Record an attestation
curl -sf -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/attestations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{"type": "unit-tests", "status": "PASSED", "details": "Smoke test attestation"}'

# 4. Retrieve the trail and verify
curl -sf "$BASE_URL/api/v1/trails/$TRAIL_ID" \
  -H "Authorization: Bearer $API_KEY" | jq .

echo "✅ Smoke test complete"
```

---

## 7. Troubleshooting

| Symptom | Likely cause | Resolution |
|---------|-------------|------------|
| `Connection refused` on port 8080 | Backend not started | Check `docker compose logs backend` or JVM startup logs |
| `Flyway migration failed` | Database schema conflict or wrong credentials | Verify `DB_*` env vars; check if database exists |
| `401 Unauthorized` on all requests | `SECURITY_ENFORCE_AUTH=true` but no key provided | Include `Authorization: Bearer <key>` header |
| `500 Internal Server Error` on startup | Invalid `AUDIT_HMAC_SECRET` or `SCM_ENCRYPTION_KEY` | Ensure `SCM_ENCRYPTION_KEY` is exactly 32 characters |
| Vault connection errors at startup | `VAULT_ENABLED=true` but Vault unreachable | Set `VAULT_ENABLED=false` or fix Vault connectivity |
| High database connection pool exhaustion | Under heavy load | Increase `spring.datasource.hikari.maximum-pool-size` |
