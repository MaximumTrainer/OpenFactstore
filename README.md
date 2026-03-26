# OpenFactstore

![CI](https://github.com/MaximumTrainer/OpenFactstore/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-blue)

A **Supply Chain Compliance Fact Store** — a full-stack web application for tracking and verifying that software artifacts (container images) meet predefined security and quality requirements before deployment. Built for DevSecOps teams in financial services and other regulated industries.

---

## Quick Start

```bash
git clone https://github.com/MaximumTrainer/OpenFactstore.git
cd OpenFactstore
docker compose up --build
```

- **API** → http://localhost:8080
- **Swagger UI** → http://localhost:8080/swagger-ui.html
- **Grafana** → http://localhost:3000 (admin / changeme)

---

## Documentation

| Guide | Description |
|-------|-------------|
| **[USER_GUIDE.md](./USER_GUIDE.md)** | Comprehensive guide: setup, tutorial, all features, security |
| **[docs/API_REFERENCE.md](./docs/API_REFERENCE.md)** | Full REST API reference (200+ endpoints) |
| **[docs/ci-integration.md](./docs/ci-integration.md)** | CI/CD integration: GitHub Actions, GitLab, Jenkins, CircleCI, Azure DevOps |
| **[DEPLOY.md](./DEPLOY.md)** | Docker, JAR, and release deployment guide |
| **[CONTRIBUTING.md](./CONTRIBUTING.md)** | Development setup, testing, and PR guidelines |
| **[SECURITY.md](./SECURITY.md)** | Vulnerability reporting and production hardening |
| **[docs/getting-started/](./docs/getting-started/)** | Step-by-step getting started series |

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

OpenFactstore enables engineering teams to define **compliance flows** — sets of required attestations (e.g. unit tests passing, security scans completed) that a container image must satisfy before it can be considered safe to deploy.

When a software artifact is built, a **trail** captures provenance metadata (Git commit SHA, branch, PR number, author). Attestations (evidence such as test results or scan reports) are linked to that trail. At any point, you can **assert** whether a given artifact digest meets all requirements for a given flow.

---

## Key Features

- **Flow Management** — Define named compliance flows with required attestation types (`junit`, `snyk`, `trivy`, …).
- **Flow Tags** — Tag flows with arbitrary key-value pairs for filtering, dashboards, and policy rules.
- **Flow Template YAML** — Version-control compliance specs as YAML templates.
- **Trail Tracking** — Record Git commit metadata (SHA, branch, PR, author) per build.
- **Artifact Management** — Track container images by SHA-256 digest, name, tag, and registry.
- **Attestations** — Attach evidence (test results, scan reports) with `PASSED`, `FAILED`, or `PENDING` status.
- **Evidence Vault** — Store evidence files with cryptographic hash verification, optionally backed by HashiCorp Vault.
- **Pull Request Attestation** — Auto-fetch PR evidence directly from GitHub/GitLab/Bitbucket.
- **Security Scan Integration** — Record structured results from Trivy, Snyk, Grype, Semgrep with per-flow thresholds.
- **OPA Policy Integration** — Upload Rego bundles and evaluate artifacts against custom Open Policy Agent policies.
- **Compliance Assertion** — Assert whether an artifact satisfies all requirements for a flow at any time.
- **Release Approval Workflow** — Require human sign-off before a trail becomes compliant.
- **Deployment Gate & Policy Engine** — Block deployments that fail compliance or policy evaluation.
- **Environment Drift Detection** — Snapshot environments, set baselines, and detect drift.
- **Allow-list Third-party Artifacts** — Exclude platform-managed images from drift alerts.
- **Organisation Multi-tenancy** — Isolate flows, users, and integrations per organisation.
- **Regulatory Compliance Framework** — Map flows to SOX, PCI-DSS, GDPR, ISO 27001 controls; generate audit reports.
- **Dry-run Safe Mode** — Preview any mutating operation without persisting data (`X-Dry-Run: true`).
- **CI/CD Integration** — Native support for GitHub Actions, GitLab CI, Jenkins, CircleCI, Azure DevOps via `X-Factstore-CI-Context` header.
- **Event Sourcing** — Append-only event log captures every state change as an immutable domain event (`FlowCreated`, `TrailCreated`, `ArtifactReported`, …). Supports full and incremental replay for rebuilding read-model projections.
- **Immutable Audit Trail** — Full chain of custody linking artifacts → trails → flows → attestations → evidence, backed by the event log.
- **Prometheus Metrics & Grafana Dashboards** — Four pre-built dashboards for compliance, security, gates, and forensics.
- **Slack & Atlassian Integrations** — Notify Slack on non-compliant trails; sync to Jira and Confluence.
- **SSO / OIDC** — Per-organisation SSO configuration (Okta, Azure AD, any OIDC provider).

---

## Architecture

Factstore is built on **Hexagonal Architecture** (Ports and Adapters) with a **CQRS + Event Sourcing** split. The core business logic is fully isolated from external systems. Dependencies always point **inward**: adapters depend on ports, ports depend on the domain — never the other way around.

The **Write** path accepts commands via v2 REST controllers, validates business rules, persists state, and appends immutable domain events to an append-only **Event Log**. The **Read** path serves queries from optimised read models. An **Event Projector** can replay the event log to rebuild read-model state from scratch or catch up incrementally.

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
│  │  adapter/inbound/web/command/ (v2 Command Controllers)  │    │
│  │  adapter/inbound/web/query/   (v2 Query Controllers)    │    │
│  │  adapter/inbound/web/         (v1 REST Controllers)     │    │
│  └────────────────┬───────────────────┬────────────────────┘    │
│       Commands    │                   │  Queries                 │
│  ┌────────────────▼───────────┐  ┌────▼────────────────────┐    │
│  │  COMMAND HANDLERS (Write)  │  │  QUERY HANDLERS (Read)  │    │
│  │  application/command/      │  │  application/query/      │    │
│  │  (FlowCommandHandler, …)  │  │  (FlowQueryHandler, …)  │    │
│  └──────┬──────────┬──────────┘  └──────────┬──────────────┘    │
│         │ save     │ append event            │ read              │
│  ┌──────▼──────┐ ┌─▼────────────────┐ ┌─────▼──────────────┐   │
│  │ JPA Entity  │ │  Event Store     │ │  Read Repositories  │   │
│  │ Repositories│ │  (IEventStore)   │ │  (Read ports)       │   │
│  └──────┬──────┘ └─┬────────────────┘ └─────┬──────────────┘   │
│         │          │                         │                   │
│  ┌──────▼──────────▼─────────────────────────▼──────────────┐   │
│  │           DRIVEN ADAPTERS (Outbound)                     │   │
│  │  adapter/outbound/persistence/ (JPA + EventStoreAdapter) │   │
│  └──────────┬──────────────────────────────────────────────┘   │
│             │                                                    │
│  ┌──────────▼──────────────────────────────────────────────┐    │
│  │  EVENT PROJECTOR (application/EventProjector)           │    │
│  │  Replays event log → rebuilds read-model state          │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────┬────────────────────────────────────────────────────┘
              │ JDBC
┌─────────────▼────────────────────────────────────────────────────┐
│                    PostgreSQL Database                            │
│   Entity tables (flows, trails, …)  +  domain_events (event log) │
└──────────────────────────────────────────────────────────────────┘
```

### Why Hexagonal Architecture + Event Sourcing?

- **Swap storage backends without touching logic.** Replace the H2 JPA adapter with a PostgreSQL or Vector DB adapter by writing a new `IFlowRepository` implementation — zero changes to `FlowService`.
- **Add new delivery mechanisms freely.** Add a REST API, CLI, or message-queue consumer by writing a new driving adapter against the inbound port interfaces.
- **Test business logic without infrastructure.** The `InMemoryFlowRepository` and `InMemoryEventStore` mock adapters let services be unit-tested in a plain JUnit test with no Spring context or database.
- **Full auditability.** Every state change is recorded as an immutable domain event in the event log — nothing is lost.
- **Rebuild read models on demand.** Replay the event log to reconstruct read-model projections from scratch whenever the schema changes.

### Backend Package Layout

```
com.factstore/
├── core/
│   ├── domain/           ← Business entities (Flow, Trail, Artifact, Attestation, EvidenceFile)
│   │   └── event/        ← Domain events (sealed DomainEvent hierarchy)
│   └── port/
│       ├── inbound/
│       │   ├── command/   ← Command handler interfaces (IFlowCommandHandler, …)
│       │   └── query/     ← Query handler interfaces (IFlowQueryHandler, …)
│       └── outbound/
│           ├── read/      ← Read-model repository interfaces
│           └── …          ← Write-model repository + IEventStore port
├── application/
│   ├── command/          ← Command handlers + EventAppender
│   └── query/            ← Query handlers
│   └── EventProjector   ← Replays event log to rebuild read models
├── adapter/
│   ├── inbound/
│   │   └── web/
│   │       ├── command/  ← v2 Command REST controllers
│   │       └── query/    ← v2 Query REST controllers
│   └── outbound/
│       └── persistence/  ← JPA adapters: entity repos + EventStoreAdapter
├── dto/
│   └── command/          ← Command DTOs and request objects
├── exception/            ← Domain exceptions and global error handler
└── config/               ← CORS and OpenAPI configuration
```

### Backend Layers

| Layer | Package | Responsibility |
|-------|---------|---------------|
| Domain | `core/domain/` | Business entities (`Flow`, `Trail`, `Artifact`, `Attestation`, `EvidenceFile`) |
| Domain Events | `core/domain/event/` | Sealed `DomainEvent` hierarchy (`FlowCreated`, `TrailCreated`, …) |
| Command Ports | `core/port/inbound/command/` | Command handler interfaces (`IFlowCommandHandler`, …) |
| Query Ports | `core/port/inbound/query/` | Query handler interfaces (`IFlowQueryHandler`, …) |
| Outbound Ports | `core/port/outbound/` | Repository interfaces + `IEventStore` (append-only event log) |
| Command Handlers | `application/command/` | Write-side use cases + `EventAppender` (dual-write: JPA entity + event log) |
| Query Handlers | `application/query/` | Read-side use cases (query read-model repositories) |
| Event Projector | `application/` | `EventProjector` — replays event log to rebuild read-model state |
| Web Adapters | `adapter/inbound/web/` | REST controllers (v1 compat + v2 command/query split) |
| Persistence Adapters | `adapter/outbound/persistence/` | JPA implementations of outbound ports + `EventStoreAdapter` |
| DTO | `dto/` | Request/response objects and command DTOs |
| Exception | `exception/` | Custom exceptions and global error handler |
| Config | `config/` | CORS policy and OpenAPI/Swagger setup |

### Dependency Rule

> **Dependencies always point inward.** The domain and application layers never import from `adapter` or any external framework. Adapters depend on port interfaces; port interfaces depend only on the domain and DTOs.

### Domain Model

```
Flow  ──< Trail ──< Artifact
               └──< Attestation ──> EvidenceFile

Every state change  ──►  domain_events (append-only Event Log)
```

- A **Flow** defines which attestation types are required.
- A **Trail** represents one software build (linked to a Flow via Git metadata).
- **Artifacts** (container images) are associated with a Trail.
- **Attestations** provide evidence (linked to a Trail) that a requirement was met.
- An **EvidenceFile** stores the actual evidence payload with its hash.
- The **Event Log** (`domain_events` table) records every command-side state change as an immutable `DomainEvent`. Events are serialized as JSON and ordered by a database-generated sequence number. The `EventProjector` can replay the log to rebuild read-model projections from scratch or catch up incrementally.

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
| Spring Boot | 3.4.13 | Web framework |
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

All REST endpoints are grouped under `/api/v1` (legacy) and `/api/v2` (CQRS command/query split) base paths.

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

Pick an open [issue](https://github.com/MaximumTrainer/OpenFactstore/issues) and open a pull request against `main`. See **[CONTRIBUTING.md](./CONTRIBUTING.md)** for development setup, code style, and PR guidelines.
