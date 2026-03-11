# Factstore — Agent Guide

This document describes the design, architecture, and development requirements for the Factstore repository. All AI coding agents and human contributors must follow these guidelines when making changes.

---

## Project Overview

**Factstore** is a full-stack compliance and attestation management system. It tracks software artifacts through *trails*, records *attestations* (evidence of security and quality checks), and asserts whether an artifact meets the requirements of a *flow* (a named compliance policy). Evidence files can be attached to attestations for audit purposes.

### Core Concepts

| Concept | Description |
|---|---|
| **Flow** | A named compliance policy that declares which attestation types are required (e.g. SAST, SCA, DAST). |
| **Trail** | A record of a software artifact's lifecycle, linked to a specific Flow and carrying git metadata (commit SHA, branch, author, PR, deployment). |
| **Attestation** | Evidence that a required check was performed, with a status of `PASSED`, `FAILED`, or `PENDING`. |
| **Artifact** | A container image or binary identified by a SHA-256 digest, associated with a Trail. |
| **Evidence File** | A file (e.g. a test report) attached to an Attestation as supporting proof. |

---

## Architecture

### Backend — Spring Boot / Kotlin

The backend follows a strict **layered architecture**:

```
HTTP Request
    │
    ▼
Controller        ← maps HTTP to service calls; minimal validation
    │
    ▼
Service           ← owns all business logic and transaction boundaries, enforces most validation
    │
    ▼
Repository        ← Spring Data JPA; no business logic here
    │
    ▼
Domain / DB       ← JPA entities mapped to H2 (dev) tables
```

Controllers never talk directly to repositories. Services own `@Transactional` boundaries. Domain objects are plain JPA entities; all request/response shapes are expressed as DTOs.

**Package layout** (`backend/src/main/kotlin/com/factstore/`):

| Package | Responsibility |
|---|---|
| `controller/` | REST endpoints — `FlowController`, `TrailController`, `AttestationController`, `AssertController`, `ArtifactController`, `ComplianceController` |
| `service/` | Business logic — `FlowService`, `TrailService`, `AttestationService`, `AssertService`, `ArtifactService`, `EvidenceVaultService`, `ComplianceService` |
| `repository/` | Spring Data JPA repositories — one per aggregate root |
| `domain/` | JPA entities — `Flow`, `Trail`, `Attestation`, `Artifact`, `EvidenceFile` |
| `dto/` | Request and response DTOs (`Dtos.kt`) |
| `exception/` | `Exceptions.kt` (domain exceptions) and `GlobalExceptionHandler.kt` |
| `config/` | `CorsConfig.kt`, `OpenApiConfig.kt` |

**Key design decisions**:
- UUIDs as primary keys for all entities.
- `ConflictException` (HTTP 409) is thrown when a uniqueness constraint is violated (e.g. duplicate Flow name).
- `NotFoundException` (HTTP 404) is thrown for any lookup that returns no result.
- `GlobalExceptionHandler` converts domain exceptions to consistent JSON error responses.
- OpenAPI / Swagger UI is served at `/swagger-ui.html`; the spec is at `/api-docs`.

### Frontend — Vue 3 / TypeScript

The frontend is a Vue 3 SPA built with Vite, Pinia, Vue Router, Axios, and Tailwind CSS.

**Source layout** (`frontend/src/`):

| Directory / File | Responsibility |
|---|---|
| `views/` | One component per route page (Dashboard, Flows, FlowDetail, TrailDetail, Assert, EvidenceVault) |
| `components/` | Shared UI components (`NavBar.vue`, `StatusBadge.vue`) |
| `api/` | Typed Axios wrappers, one module per backend resource |
| `types/index.ts` | Centralised TypeScript type definitions mirroring backend DTOs |
| `router/index.ts` | Vue Router route table |
| `main.ts` | App bootstrap (Vue, Pinia, Router) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Kotlin 2.0 (JVM 21) |
| Backend framework | Spring Boot 3.2 |
| Build tool | Gradle 8 (Kotlin DSL) |
| Database (dev) | H2 in-memory |
| ORM | Spring Data JPA / Hibernate |
| API docs | SpringDoc OpenAPI 2.5 |
| Testing (backend) | JUnit 5 + Spring Boot Test + Mockito-Kotlin |
| Frontend language | TypeScript 5.4 |
| Frontend framework | Vue 3.4 |
| Bundler | Vite 5 |
| HTTP client | Axios 1.6 |
| State management | Pinia 2.1 |
| CSS | Tailwind CSS 3.4 |
| E2E tests | Playwright 1.44 |
| CI | GitHub Actions |

---

## Test-Driven Development — Red → Green → Refactor

**Every code change must follow the red-green-refactor cycle.** No production code may be written without a failing test that motivates it.

### The Cycle

1. **Red** — Write a test that describes the desired behaviour. Run it and confirm it fails (compilation errors count as red). Commit the failing test.
2. **Green** — Write the *minimum* production code required to make the failing test pass. Do not write more than needed. Run the full test suite and confirm all tests pass.
3. **Refactor** — Improve the code (naming, structure, duplication) without changing observable behaviour. The test suite must remain green throughout.

Repeat this cycle for every logical unit of work, no matter how small.

### Backend Tests

Backend tests live in `backend/src/test/kotlin/com/factstore/`. They use `@SpringBootTest` with `@Transactional` rollback so each test runs against a clean database state. Do not use mocks unless testing code that cannot run without an external system.

**Run all backend tests:**
```bash
cd backend
./gradlew test
```

**Run a single test class:**
```bash
./gradlew test --tests "com.factstore.FlowServiceTest"
```

**Test naming convention** — use backtick names that read as plain English sentences describing behaviour:
```kotlin
@Test
fun `create flow with duplicate name throws ConflictException`() { … }
```

**What to test** — focus tests at the Service layer where business rules live. Test Controllers only when HTTP-level concerns (status codes, request validation) need coverage. Repositories require testing only for custom query methods.

### Frontend Tests

End-to-end tests live in `frontend/e2e/` and are run with Playwright against a live dev server.

**Run E2E tests:**
```bash
cd frontend
npm run test:e2e
```

The dev server (`npm run dev`) must be running before executing E2E tests; Playwright does not start it automatically in any environment (including CI), so any workflow that runs E2E tests must start the dev server explicitly before invoking `npm run test:e2e`.

---

## Build & Lint

**Backend build (includes tests):**
```bash
cd backend
./gradlew build
```

**Frontend build (includes TypeScript type-check):**
```bash
cd frontend
npm ci
npm run build
```

TypeScript compilation errors are treated as build failures. Keep `npm run build` green at all times.

---

## CI Pipeline

GitHub Actions runs two parallel jobs on every push to `main` or any `copilot/**` branch, and on every pull request targeting `main`:

- **Backend Build & Test** — `./gradlew build` (compiles + runs all JUnit tests).
- **Frontend Build** — `npm ci && npm run build` (installs deps + compiles TypeScript).

Both jobs must be green before a PR is merged.

---

## Adding or Changing Code — Checklist

Before opening a pull request, verify each item:

- [ ] A failing test was written **before** the production code (Red step completed).
- [ ] All new behaviour is covered by at least one test.
- [ ] All existing tests still pass (`./gradlew test` and `npm run build`).
- [ ] No business logic has been placed in Controllers or Repositories.
- [ ] New endpoints follow the existing REST conventions and return appropriate HTTP status codes.
- [ ] New domain exceptions follow the existing exception pattern and are handled by `GlobalExceptionHandler`.
- [ ] New TypeScript types are added to `frontend/src/types/index.ts`.
- [ ] CI is green on the pull request.
