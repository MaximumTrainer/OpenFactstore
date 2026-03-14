# Factstore — Deployment Guide

A user should be able to download a single image and have Factstore running in a production-ready state **within 3–5 minutes** using only the command line.

---

## Table of Contents

- [Quick Start (Docker — recommended)](#quick-start-docker--recommended)
- [Quick Start (JAR — requires Java 21)](#quick-start-jar--requires-java-21)
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

| Job | Description |
|-----|-------------|
| `build-and-test` | Installs dependencies, builds frontend, embeds it into the backend, runs `./gradlew build` (includes tests), uploads the JAR as a workflow artifact |
| `docker` | Builds the multi-stage Docker image; pushes to GHCR only on tag pushes |
| `release` | Creates a GitHub Release with the JAR; runs only on `v*` tags |

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

### Docker environment variable example

```bash
docker run -d -p 8080:8080 \
  -e SERVER_PORT=8080 \
  ghcr.io/maximumtrainer/factstore:latest
```
