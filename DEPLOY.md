# Factstore — Deployment Guide

A user should be able to download a single image and have Factstore running in a production-ready state **within 3–5 minutes** using only the command line.

---

## Table of Contents

- [Quick Start (Docker — recommended)](#quick-start-docker--recommended)
- [Quick Start (JAR — requires Java 21)](#quick-start-jar--requires-java-21)
- [Terraform Infrastructure Bootstrap](#terraform-infrastructure-bootstrap)
- [CLI Flags](#cli-flags)
- [CI/CD Pipeline](#cicd-pipeline)
- [Build From Source](#build-from-source)
- [Verification Checklist](#verification-checklist)
- [Configuration Reference](#configuration-reference)

---

## Quick Start (Docker — recommended)

The Docker image is self-contained: it bundles the JVM, the compiled backend, **and** the compiled frontend. No pre-installed software is required beyond Docker.

### 1 — Pull and run the latest image

```bash
docker pull ghcr.io/maximumtrainer/factstore:latest
docker run -d \
  --name factstore \
  -p 8080:8080 \
  ghcr.io/maximumtrainer/factstore:latest
```

The service is ready when you see a log line containing `Started FactstoreApplication`.

### 2 — Open the UI

```
http://localhost:8080
```

### 3 — Verify (see [Verification Checklist](#verification-checklist))

```bash
curl -fs http://localhost:8080/actuator/health 2>/dev/null \
  || curl -fs http://localhost:8080/api-docs 2>/dev/null | head -c 80
```

### Pinning to a specific version

```bash
docker run -d -p 8080:8080 ghcr.io/maximumtrainer/factstore:1.0.0
```

### Custom port

```bash
docker run -d -p 9090:9090 \
  ghcr.io/maximumtrainer/factstore:latest \
  --server.port=9090
```

---

## Quick Start (JAR — requires Java 21)

Download the JAR from the [Releases page](https://github.com/MaximumTrainer/Factstore/releases) and run:

```bash
java -jar factstore-<version>.jar
```

### Show help

```bash
java -jar factstore-<version>.jar --help
```

### Show version

```bash
java -jar factstore-<version>.jar --version
```

### Custom port

```bash
java -jar factstore-<version>.jar --server.port=9090
```

---

## Terraform Infrastructure Bootstrap

The `infra/` directory contains a Terraform configuration that uses the
[OpenFactstore Terraform provider](terraform/) to bootstrap a complete Factstore
instance with a standard set of resources.

### What it provisions

| Resource | Name | Purpose |
|---|---|---|
| Organisation | `openfactstore` | Root organisation |
| Logical Environment | `production-group` | Logical grouping for production environments |
| Environment | `staging` (K8S) | Staging Kubernetes cluster |
| Environment | `production` (K8S) | Production Kubernetes cluster |
| Policy | `baseline-requirements` | Provenance + junit/snyk attestations |
| Policy | `production-requirements` | Full compliance — provenance + trail + pull-request |
| Policy Attachment | staging → baseline | Enforces baseline policy on staging |
| Policy Attachment | production → production | Enforces production policy on production |
| Flow | `backend-ci` | Backend CI pipeline flow |
| Flow | `frontend-ci` | Frontend CI pipeline flow |

### Prerequisites

1. A running Factstore instance (see [Quick Start](#quick-start-docker--recommended))
2. Terraform ≥ 1.6 ([install](https://developer.hashicorp.com/terraform/install))
3. The Factstore Terraform provider built locally (see below)

### Build the Terraform provider

```bash
cd terraform
go build -o terraform-provider-factstore .
PROVIDER_DIR=~/.terraform.d/plugins/registry.terraform.io/MaximumTrainer/factstore/1.0.0/linux_amd64
mkdir -p "$PROVIDER_DIR"
cp terraform-provider-factstore "$PROVIDER_DIR/"
```

Configure Terraform to use the local build by creating `~/.terraformrc`:

```hcl
provider_installation {
  dev_overrides {
    "MaximumTrainer/factstore" = "~/.terraform.d/plugins/registry.terraform.io/MaximumTrainer/factstore/1.0.0/linux_amd64"
  }
  direct {}
}
```

### Apply

```bash
cd infra
terraform init
terraform apply \
  -var="factstore_url=http://localhost:8080" \
  -var="factstore_token="      # omit if SECURITY_ENFORCE_AUTH is not set
```

Or use environment variables instead of `-var` flags:

```bash
export FACTSTORE_BASE_URL=http://localhost:8080
export FACTSTORE_API_TOKEN=your-token   # omit if auth not enforced
terraform apply
```

### Variables

| Variable | Default | Description |
|---|---|---|
| `factstore_url` | `http://localhost:8080` | Factstore API base URL |
| `factstore_token` | `""` | API token (required when `SECURITY_ENFORCE_AUTH=true`) |
| `org_slug` | `openfactstore` | URL-safe organisation identifier |
| `org_name` | `OpenFactstore` | Organisation display name |

### Destroy

```bash
terraform destroy \
  -var="factstore_url=http://localhost:8080" \
  -var="factstore_token="
```

### CI verification

The `.github/workflows/terraform-verify.yml` workflow runs on every push and PR. It:

1. Starts a PostgreSQL service container
2. Builds and starts the Factstore backend
3. Builds the Terraform provider from source
4. Runs `terraform apply` against the live backend
5. Verifies the created resources exist via the API
6. Runs `terraform destroy` to clean up

---

## CLI Flags

| Flag | Description |
|------|-------------|
| `--help` | Print usage instructions and exit |
| `--version` | Print the application version and exit |
| `--server.port=<port>` | Override the HTTP port (default: `8080`) |
| `--spring.profiles.active=<profile>` | Activate a Spring profile |

---

## CI/CD Pipeline

The deployment pipeline lives at [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml).

### Triggers

| Event | What happens |
|-------|-------------|
| Push to `main` | Build, test, build Docker image (image is **not** pushed) |
| Push of a `v*` tag (e.g. `v1.0.0`) | Build, test, push Docker image to GHCR, create GitHub Release with JAR attached |

### Creating a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

This triggers the full pipeline and:

1. Runs all backend unit tests and frontend build.
2. Builds a multi-stage Docker image and pushes it to `ghcr.io/maximumtrainer/factstore`.
3. Creates a GitHub Release with the executable JAR attached.

### Pipeline jobs

| Job | Description | Environment |
|-----|-------------|-------------|
| `build-and-test` | Installs dependencies, builds frontend, embeds it into the backend, runs `./gradlew build` (includes tests), uploads the JAR as a workflow artifact | — |
| `docker` | Builds the multi-stage Docker image; pushes to GHCR only on tag pushes | **staging** |
| `release` | Creates a GitHub Release with the JAR; runs only on `v*` tags | **production** |

The `docker` job targets the **staging** GitHub Environment and the `release` job targets
**production**. Configure environment protection rules (required reviewers, deployment branch
restrictions) in the repository **Settings → Environments** to gate production releases.

---

## Build From Source

### Prerequisites

- Java 21 (Eclipse Temurin recommended)
- Node.js 20 + npm
- Docker (optional — for image builds)

### 1 — Clone the repository

```bash
git clone https://github.com/MaximumTrainer/Factstore.git
cd Factstore
```

### 2 — Build the frontend

```bash
cd frontend
npm ci
npm run build
cd ..
```

### 3 — Embed frontend and build the backend JAR

```bash
cp -r frontend/dist/. backend/src/main/resources/static/
cd backend
./gradlew build
```

The executable JAR is produced at:

```
backend/build/libs/factstore-<version>.jar
```

### 4 — Run the JAR

```bash
java -jar backend/build/libs/factstore-*.jar
```

### 5 — Build the Docker image locally

```bash
docker build -t factstore:local .
docker run -d -p 8080:8080 --name factstore factstore:local
```

---

## Verification Checklist

After starting Factstore, verify the service is healthy:

- [ ] **UI loads** — open [http://localhost:8080](http://localhost:8080) in a browser (or `curl -I http://localhost:8080` returns `200 OK`)
- [ ] **REST API responds** — `curl -s http://localhost:8080/api/v1/flows` returns a JSON array (`[]` when empty)
- [ ] **Swagger UI accessible** — open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- [ ] **OpenAPI spec available** — `curl -s http://localhost:8080/api-docs` returns a JSON document

One-liner verification:

```bash
echo "--- UI ---"        && curl -o /dev/null -s -w "%{http_code}\n" http://localhost:8080/
echo "--- Flows API ---" && curl -s http://localhost:8080/api/v1/flows
echo "--- OpenAPI ---"   && curl -s http://localhost:8080/api-docs | grep -o '"openapi":"[^"]*"'
```

Expected output:

```
--- UI ---
200
--- Flows API ---
[]
--- OpenAPI ---
"openapi":"3.0.1"
```

---

## Configuration Reference

All standard Spring Boot properties can be passed as command-line flags or environment variables.

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:h2:mem:factstore` | JDBC URL (H2 in-memory) |
| `spring.datasource.username` | `sa` | DB username |
| `spring.datasource.password` | _(empty)_ | DB password |
| `spring.h2.console.enabled` | `true` | Enable H2 web console at `/h2-console` |

#### PostgreSQL configuration

| Environment Variable | Description |
|----------------------|-------------|
| `DB_HOST` | PostgreSQL hostname (default: `localhost`) |
| `DB_PORT` | PostgreSQL port (default: `5432`) |
| `DB_NAME` | Database name (default: `factstore`) |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

#### RabbitMQ configuration (CQRS event bus)

| Environment Variable | Description |
|----------------------|-------------|
| `RABBITMQ_HOST` | RabbitMQ hostname (default: `localhost`) |
| `RABBITMQ_PORT` | RabbitMQ AMQP port (default: `5672`) |
| `RABBITMQ_USERNAME` | RabbitMQ user (default: `guest`) |
| `RABBITMQ_PASSWORD` | RabbitMQ password (default: `guest`) |

#### Event publisher

| Environment Variable | Description |
|----------------------|-------------|
| `FACTSTORE_EVENTS_PUBLISHER` | `logging` (default), `rabbitmq` (production CQRS), `inmemory` (tests), `none` |

#### HashiCorp Vault integration

| Environment Variable | Description |
|----------------------|-------------|
| `VAULT_ENABLED` | Set to `true` to enable Vault-backed secrets |
| `VAULT_ADDR` | Vault server address (e.g. `https://vault.example.com:8200`) |
| `VAULT_TOKEN` | Vault access token |

#### Grafana / monitoring

| Environment Variable | Description |
|----------------------|-------------|
| `GF_SECURITY_ADMIN_PASSWORD` | Grafana admin password (default: `admin`) |

#### OAuth / GitHub SSO

| Environment Variable | Description |
|----------------------|-------------|
| `GITHUB_CLIENT_ID` | GitHub OAuth app client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth app client secret |

---

## CQRS Deployment (Dual-Service Architecture)

For production, Factstore runs as two separate services sharing a RabbitMQ event bus:

### Network Topology

```
                    ┌──────────────┐
                    │   Clients    │
                    └──────┬───────┘
              POST/PUT/DELETE │  GET
          ┌──────────────────┴────────────────┐
          ▼                                    ▼
  ┌───────────────┐                   ┌───────────────┐
  │ Command :8080 │──── RabbitMQ ────►│  Query :8081  │
  └───────┬───────┘                   └───────┬───────┘
          │                                    │
  ┌───────▼───────┐                   ┌───────▼───────┐
  │  PostgreSQL   │                   │  PostgreSQL   │
  │  (Write DB)   │                   │  (Read DB)    │
  │    :5432      │                   │    :5433      │
  └───────────────┘                   └───────────────┘
```

### Docker Compose (recommended)

```bash
docker compose up --build
```

This starts:
- **postgres-command** — Write database (port 5432)
- **postgres-query** — Read database (port 5433)
- **rabbitmq** — Event bus (AMQP 5672, Management UI 15672)
- **backend-command** — Command service (port 8080)
- **backend-query** — Query service (port 8081)

### Event-Driven Synchronization

1. A command (POST/PUT/DELETE) arrives at the **Command service** (:8080)
2. The command handler persists the entity + appends a domain event to the event store
3. The `EventAppender` publishes the event to the `IDomainEventBus` (RabbitMQ)
4. The **Query service** (:8081) `RabbitMqEventConsumer` receives the event
5. The `ReadModelProjector` applies the event to the read database

### CLI Configuration

The CLI supports separate hosts for read and write operations:

```bash
# Configure with separate command and query hosts
factstore configure
# Or use flags:
factstore --host https://command.example.com --query-host https://query.example.com flows list
# Or environment variables:
export FACTSTORE_HOST=https://command.example.com
export FACTSTORE_QUERY_HOST=https://query.example.com
```

When `--query-host` is set, GET requests are routed to the query service and all other requests to the command service.

### Post-Deployment Verification

```bash
# 1. Verify command service health
curl -fs http://localhost:8080/actuator/health

# 2. Verify query service health
curl -fs http://localhost:8081/actuator/health

# 3. Create a flow via command service and verify it appears on query service
curl -X POST http://localhost:8080/api/v2/flows \
  -H 'Content-Type: application/json' \
  -d '{"name":"verify-cqrs","description":"Post-deployment verification"}'

# Wait for event propagation (typically < 1 second)
sleep 2

# 4. Read the flow from query service
curl -s http://localhost:8081/api/v2/flows | grep verify-cqrs

# 5. Verify RabbitMQ is healthy
curl -fs http://localhost:15672/api/healthchecks/node \
  -u guest:guest
```

### Docker environment variable example

```bash
docker run -d -p 8080:8080 \
  -e SERVER_PORT=8080 \
  ghcr.io/maximumtrainer/factstore:latest
```
