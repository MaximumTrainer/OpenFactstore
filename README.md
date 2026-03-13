# Factstore

A **Supply Chain Compliance Fact Store** — a full-stack web application for tracking and verifying that software artifacts (container images) meet predefined security and quality requirements before deployment.

---

## Getting Started

The [Getting Started guide](./docs/getting-started/01-overview.md) walks you through every concept from first principles:

1. [Overview](./docs/getting-started/01-overview.md)
2. [Setup & Access](./docs/getting-started/02-setup.md)
3. [Authentication](./docs/getting-started/03-authentication.md) *(coming soon)*
4. [Flows](./docs/getting-started/04-flows.md)
5. [Trails](./docs/getting-started/05-trails.md)
6. [Artifacts](./docs/getting-started/06-artifacts.md)
7. [Attestations](./docs/getting-started/07-attestations.md)
8. [Environments](./docs/getting-started/08-environments.md) *(coming soon)*
9. [Policies](./docs/getting-started/09-policies.md) *(coming soon)*
10. [Approvals](./docs/getting-started/10-approvals.md) *(coming soon)*
11. [Next Steps & Roadmap](./docs/getting-started/11-next-steps.md)

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [PostgreSQL Setup](#postgresql-setup)
- [Building the Project](#building-the-project)
- [Running the Project](#running-the-project)
- [Running Tests](#running-tests)
- [API Documentation](#api-documentation)
- [Deployment](#deployment)
- [Contributing](#contributing)

---

## Overview

Factstore enables engineering teams to define **compliance flows** — sets of required attestations (e.g. unit tests passing, security scans completed) that a container image must satisfy before it can be considered safe to deploy.

When a software artifact is built, a **trail** captures provenance metadata (Git commit SHA, branch, PR number, author). Attestations (evidence such as test results or scan reports) are linked to that trail. At any point, you can **assert** whether a given artifact digest meets all requirements for a given flow.

---

## Key Features

- **Flow Management** — Define named compliance flows with a list of required attestation types (e.g. `junit`, `snyk`, `trivy`).
- **Trail Tracking** — Record Git commit metadata (SHA, branch, PR, author, deployment actor) tied to each build.
- **Artifact Management** — Track container images by their SHA-256 digest, name, tag, and registry.
- **Attestations** — Attach evidence (test results, scan reports) to an artifact's trail with a `PASSED`, `FAILED`, or `PENDING` status.
- **Evidence Vault** — Store evidence files with cryptographic hash verification and timestamping.
- **Compliance Assertion** — Query at any time whether a specific artifact digest satisfies all required attestations for a flow.
- **Audit Trail** — Full chain of custody linking artifacts → trails → flows → attestations → evidence files.

---

## Architecture

Factstore is built on **Hexagonal Architecture** (Ports and Adapters), where the core business logic is fully isolated from external systems. Dependencies always point **inward**: adapters depend on ports, ports depend on the domain — never the other way around.

```
┌─────────────────────────────────────────────────────────────────┐
│                    Frontend (Vue 3 SPA)                         │
│              Browser  ─►  Vite Dev Server :5173                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP / REST (Axios)
┌──────────────────────────────▼──────────────────────────────────┐
│                  Backend (Spring Boot :8080)                     │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │            DRIVING ADAPTERS (Inbound)                   │    │
│  │         adapter/inbound/web/ (REST Controllers)         │    │
│  └───────────────────────────┬─────────────────────────────┘    │
│                              │ calls via                        │
│  ┌───────────────────────────▼─────────────────────────────┐    │
│  │              INBOUND PORTS (Driving)                    │    │
│  │         core/port/inbound/ (IFlowService, etc.)         │    │
│  └───────────────────────────┬─────────────────────────────┘    │
│                              │ implemented by                   │
│  ┌───────────────────────────▼─────────────────────────────┐    │
│  │             APPLICATION LAYER (Use Cases)               │    │
│  │         application/ (FlowService, AssertService, …)    │    │
│  └──────────┬──────────────────────────────────────────────┘    │
│             │ calls via                                         │
│  ┌──────────▼──────────────────────────────────────────────┐    │
│  │             OUTBOUND PORTS (Driven)                     │    │
│  │      core/port/outbound/ (IFlowRepository, etc.)        │    │
│  └──────────┬──────────────────────────────────────────────┘    │
│             │ implemented by                                    │
│  ┌──────────▼──────────────────────────────────────────────┐    │
│  │           DRIVEN ADAPTERS (Outbound)                    │    │
│  │  adapter/outbound/persistence/ (JPA Repository Adapters)│    │
│  └──────────┬──────────────────────────────────────────────┘    │
└─────────────┼────────────────────────────────────────────────────┘
              │ JDBC
┌─────────────▼────────────────────────────────────────────────────┐
│                    PostgreSQL Database                            │
└──────────────────────────────────────────────────────────────────┘
```

### Why Hexagonal Architecture?

- **Swap storage backends without touching logic.** Replace the H2 JPA adapter with a PostgreSQL or Vector DB adapter by writing a new `IFlowRepository` implementation — zero changes to `FlowService`.
- **Add new delivery mechanisms freely.** Add a REST API, CLI, or message-queue consumer by writing a new driving adapter against the inbound port interfaces.
- **Test business logic without infrastructure.** The `InMemoryFlowRepository` mock adapter lets `FlowService` be unit-tested in a plain JUnit test with no Spring context or database.

### Backend Package Layout

```
com.factstore/
├── core/
│   ├── domain/           ← Business entities (Flow, Trail, Artifact, Attestation, EvidenceFile)
│   └── port/
│       ├── inbound/      ← Driving port interfaces (IFlowService, IAssertService, …)
│       └── outbound/     ← Driven port interfaces (IFlowRepository, ITrailRepository, …)
├── application/          ← Use case implementations (FlowService, AssertService, …)
├── adapter/
│   ├── inbound/
│   │   └── web/          ← Driving adapters: REST Controllers
│   └── outbound/
│       └── persistence/  ← Driven adapters: JPA Repository + Adapter classes
├── dto/                  ← Request / response DTOs
├── exception/            ← Domain exceptions and global error handler
└── config/               ← CORS and OpenAPI configuration
```

### Backend Layers

| Layer | Package | Responsibility |
|-------|---------|---------------|
| Domain | `core/domain/` | Business entities (`Flow`, `Trail`, `Artifact`, `Attestation`, `EvidenceFile`) |
| Inbound Ports | `core/port/inbound/` | Service interfaces (`IFlowService`, `IAssertService`, …) |
| Outbound Ports | `core/port/outbound/` | Repository interfaces (`IFlowRepository`, `ITrailRepository`, …) |
| Application | `application/` | Use case implementations (depend only on port interfaces) |
| Web Adapters | `adapter/inbound/web/` | REST controllers (depend on inbound port interfaces) |
| Persistence Adapters | `adapter/outbound/persistence/` | JPA implementations of outbound ports |
| DTO | `dto/` | Request/response objects decoupled from entities |
| Exception | `exception/` | Custom exceptions and global error handler |
| Config | `config/` | CORS policy and OpenAPI/Swagger setup |

### Dependency Rule

> **Dependencies always point inward.** The domain and application layers never import from `adapter` or any external framework. Adapters depend on port interfaces; port interfaces depend only on the domain and DTOs.

### Domain Model

```
Flow  ──< Trail ──< Artifact
               └──< Attestation ──> EvidenceFile
```

- A **Flow** defines which attestation types are required.
- A **Trail** represents one software build (linked to a Flow via Git metadata).
- **Artifacts** (container images) are associated with a Trail.
- **Attestations** provide evidence (linked to a Trail) that a requirement was met.
- An **EvidenceFile** stores the actual evidence payload with its hash.

### Frontend Layers

| Layer | Location | Responsibility |
|-------|----------|---------------|
| Views | `src/views/` | Page-level components (`Flows`, `Trails`, `Assert`, `EvidenceVault`, `Dashboard`) |
| Components | `src/components/` | Reusable UI (`NavBar`, `StatusBadge`) |
| API clients | `src/api/` | Axios modules per resource (`flows`, `trails`, `artifacts`, `attestations`, `assert`) |
| Router | `src/router/` | Client-side routing with Vue Router |
| Types | `src/types/` | Shared TypeScript interfaces mirroring backend DTOs |

---

## Technology Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.0.20 | Primary language |
| Spring Boot | 3.2.5 | Web framework |
| Spring Data JPA / Hibernate | — | ORM and data access |
| PostgreSQL | 16 | Persistent relational database |
| Flyway | — | Versioned schema migrations |
| HikariCP | — | Connection pooling (bundled with Spring Boot) |
| Springdoc OpenAPI | 2.5.0 | Auto-generated Swagger docs |
| JUnit 5 + Mockito-Kotlin | 5.4.0 | Unit testing |
| H2 Database | — | In-memory database for unit tests |
| Java | 21 | Runtime |
| Gradle (Kotlin DSL) | — | Build tool |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| Vue 3 | 3.4.0 | UI framework (Composition API) |
| TypeScript | 5.4.0 | Type-safe JavaScript |
| Vite | 5.0.0 | Build tool and dev server |
| Vue Router | 4.3.0 | Client-side routing |
| Pinia | 2.1.0 | State management |
| Axios | 1.6.0 | HTTP client |
| Tailwind CSS | 3.4.0 | Utility-first CSS framework |
| Playwright | 1.44.0 | End-to-end tests |
| Node.js | 20 | Runtime |

---

## Prerequisites

- **Java 21** (e.g. Eclipse Temurin)
- **Node.js 20** and npm
- **PostgreSQL 16** (for production/local development — see [PostgreSQL Setup](#postgresql-setup) below)
- **Docker & Docker Compose** (recommended for local PostgreSQL)

---

## PostgreSQL Setup

Factstore uses **PostgreSQL 16** for persistent storage. Schema migrations are managed by **Flyway** and run automatically on startup.

### Option A: Docker Compose (Recommended)

The included `docker-compose.yml` starts PostgreSQL and the backend (built from the root `Dockerfile`):

```bash
# Start only the database (for local development with `./gradlew bootRun`)
docker compose up -d postgres

# Start the full stack (database + backend)
docker compose up --build
```

The Docker Compose stack uses these credentials — set them as environment variables or update `docker-compose.yml` before starting:

| Variable | Default in Compose |
|----------|--------------------|
| `DB_HOST` | `postgres` (Docker network) / `localhost` (from host) |
| `DB_PORT` | `5432` |
| `DB_NAME` | `factstore` |
| `DB_USERNAME` | `factstore` *(set explicitly — no application default)* |
| `DB_PASSWORD` | `factstore` *(set explicitly — no application default)* |

### Option B: External PostgreSQL

Set environment variables before starting the backend:

```bash
export DB_HOST=your-db-host
export DB_PORT=5432
export DB_NAME=factstore
export DB_USERNAME=factstore
export DB_PASSWORD=your-password

cd backend
./gradlew bootRun
```

### Database Schema

All schema changes are managed as numbered Flyway migrations in `backend/src/main/resources/db/migration/`. Migrations run automatically on application startup. Do not edit committed migration scripts — add a new numbered script instead.

### Unit Tests

Unit tests use an **H2 in-memory database** and do not require PostgreSQL. The test configuration in `backend/src/test/resources/application.yml` overrides the datasource automatically when running tests.

---

## Building the Project

### Backend

```bash
cd backend
./gradlew build          # Compile, run tests, and produce the JAR
./gradlew clean build    # Clean then build
./gradlew assemble       # Build without running tests
```

### Frontend

```bash
cd frontend
npm ci                   # Install exact dependency versions (recommended)
npm run build            # Type-check and produce a production build in dist/
```

---

## Running the Project

### Backend

```bash
cd backend
./gradlew bootRun        # Start the Spring Boot dev server on port 8080
```

Or run the built JAR directly:

```bash
java -jar backend/build/libs/factstore-0.0.1-SNAPSHOT.jar
```

### Frontend

```bash
cd frontend
npm run dev              # Start the Vite dev server on port 5173
```

Open [http://localhost:5173](http://localhost:5173) in your browser. The frontend proxies API calls to the backend at `http://localhost:8080`.

> **Note:** The backend must be running before the frontend can load data.

---

## Running Tests

### Backend Unit Tests

```bash
cd backend
./gradlew test                              # Run all tests
./gradlew test --info                       # Verbose output
./gradlew test --tests "com.factstore.*"    # Run tests matching a pattern
```

Test reports are written to `backend/build/reports/tests/test/`.

### Frontend End-to-End Tests

```bash
cd frontend
npm run test:e2e         # Run Playwright end-to-end tests
```

---

## API Documentation

When the backend is running, interactive API documentation is available via Swagger UI:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

All REST endpoints are grouped under the `/api/v1` base path.

---

## CI/CD

GitHub Actions runs on every push to `main` or any `copilot/**` branch, and on pull requests targeting `main`. The pipeline runs backend build + tests and frontend build in parallel.

See [`.github/workflows/ci.yml`](.github/workflows/ci.yml) for details.

---

## Deployment

Pre-built Docker images and executable JARs are available on the [Releases page](https://github.com/MaximumTrainer/Factstore/releases).

```bash
# Pull and run the latest Docker image (no Java required)
docker run -d -p 8080:8080 ghcr.io/maximumtrainer/factstore:latest
```

See **[DEPLOY.md](./DEPLOY.md)** for the full deployment guide, including:

- Docker quick-start
- JAR quick-start (`--help` / `--version` flags)
- CI/CD pipeline details and how to create a release
- Step-by-step build-from-source instructions
- Verification checklist

---

## Contributing

Pick an open [issue](https://github.com/MaximumTrainer/Factstore/issues) and open a pull request against `main`.
