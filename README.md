# Factstore

A **Supply Chain Compliance Fact Store** — a full-stack web application for tracking and verifying that software artifacts (container images) meet predefined security and quality requirements before deployment.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Building the Project](#building-the-project)
- [Running the Project](#running-the-project)
- [Running Tests](#running-tests)
- [API Documentation](#api-documentation)

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

Factstore follows a standard **three-tier architecture**:

```
┌─────────────────────────────────────────┐
│          Frontend (Vue 3 SPA)           │
│   Browser  ─►  Vite Dev Server :5173    │
└───────────────────┬─────────────────────┘
                    │ HTTP / REST (Axios)
┌───────────────────▼─────────────────────┐
│        Backend (Spring Boot API)        │
│            REST API  :8080              │
│                                         │
│  ┌──────────────────────────────────┐   │
│  │  Controllers  (REST endpoints)   │   │
│  │  Services     (business logic)   │   │
│  │  Repositories (data access)      │   │
│  │  Entities     (JPA/Hibernate)    │   │
│  └──────────────────────────────────┘   │
└───────────────────┬─────────────────────┘
                    │ JDBC
┌───────────────────▼─────────────────────┐
│       H2 In-Memory Database             │
└─────────────────────────────────────────┘
```

### Backend Layers

| Layer | Package | Responsibility |
|-------|---------|---------------|
| Controller | `controller/` | REST endpoints, request validation |
| Service | `service/` | Business logic, compliance checking |
| Repository | `repository/` | JPA data access |
| Domain | `domain/` | JPA entities (`Flow`, `Trail`, `Artifact`, `Attestation`, `EvidenceFile`) |
| DTO | `dto/` | Request/response objects decoupled from entities |
| Exception | `exception/` | Custom exceptions and global error handler |
| Config | `config/` | CORS policy and OpenAPI/Swagger setup |

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
| Kotlin | 1.9 | Primary language |
| Spring Boot | 3.2.5 | Web framework |
| Spring Data JPA / Hibernate | — | ORM and data access |
| H2 Database | — | In-memory relational database |
| Springdoc OpenAPI | 2.5.0 | Auto-generated Swagger docs |
| JUnit 5 + Mockito-Kotlin | 5.4.0 | Unit testing |
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
- **H2 Console** (dev only): [http://localhost:8080/h2-console](http://localhost:8080/h2-console) — username `sa`, no password

All REST endpoints are grouped under the `/api/v1` base path.

---

## CI/CD

GitHub Actions runs on every push to `main` or any `copilot/**` branch, and on pull requests targeting `main`. The pipeline runs backend build + tests and frontend build in parallel.

See [`.github/workflows/ci.yml`](.github/workflows/ci.yml) for details.