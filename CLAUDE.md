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

### Hexagonal Architecture (Ports and Adapters)

The backend is structured as a **Hexagonal Architecture**. The Dependency Rule is strict: **dependencies always point inward**. Adapters know about ports; ports know about the domain. The domain and application layers know about nothing outside themselves.

```
   ┌─────────────────────────────────────────────┐
   │            Driving Adapters                  │
   │    adapter/inbound/web/ (REST Controllers)   │
   └──────────────────┬──────────────────────────┘
                      │ calls via inbound port
   ┌──────────────────▼──────────────────────────┐
   │             Inbound Ports                    │
   │    core/port/inbound/ (IFlowService, …)      │
   └──────────────────┬──────────────────────────┘
                      │ implemented by
   ┌──────────────────▼──────────────────────────┐
   │           Application Layer                  │
   │    application/ (FlowService, …)             │
   └──────────────────┬──────────────────────────┘
                      │ calls via outbound port
   ┌──────────────────▼──────────────────────────┐
   │             Outbound Ports                   │
   │   core/port/outbound/ (IFlowRepository, …)   │
   └──────────────────┬──────────────────────────┘
                      │ implemented by
   ┌──────────────────▼──────────────────────────┐
   │            Driven Adapters                   │
   │  adapter/outbound/persistence/ (JPA Adapters)│
   └─────────────────────────────────────────────┘
```

### Package Layout (`backend/src/main/kotlin/com/factstore/`)

| Package | Layer | Responsibility |
|---|---|---|
| `core/domain/` | Domain | Business entities — `Flow`, `Trail`, `Attestation`, `Artifact`, `EvidenceFile` |
| `core/port/inbound/` | Inbound Ports | Service interfaces — `IFlowService`, `ITrailService`, `IAttestationService`, `IArtifactService`, `IAssertService`, `IComplianceService`, `IEvidenceVaultService` |
| `core/port/outbound/` | Outbound Ports | Repository interfaces — `IFlowRepository`, `ITrailRepository`, `IAttestationRepository`, `IArtifactRepository`, `IEvidenceFileRepository` |
| `application/` | Application | Use case implementations — `FlowService`, `TrailService`, `AttestationService`, `ArtifactService`, `AssertService`, `EvidenceVaultService`, `ComplianceService` |
| `adapter/inbound/web/` | Driving Adapter | REST controllers — `FlowController`, `TrailController`, `AttestationController`, `ArtifactController`, `AssertController`, `ComplianceController` |
| `adapter/outbound/persistence/` | Driven Adapter | JPA repository interfaces (`*RepositoryJpa`) + adapter classes (`*RepositoryAdapter`) |
| `dto/` | Shared | Request and response DTOs (`Dtos.kt`) |
| `exception/` | Shared | `Exceptions.kt` + `GlobalExceptionHandler.kt` |
| `config/` | Shared | `CorsConfig.kt`, `OpenApiConfig.kt` |

### Key Design Decisions

- UUIDs as primary keys for all entities.
- `ConflictException` (HTTP 409) is thrown when a uniqueness constraint is violated (e.g. duplicate Flow name).
- `NotFoundException` (HTTP 404) is thrown for any lookup that returns no result.
- `GlobalExceptionHandler` converts domain exceptions to consistent JSON error responses.
- OpenAPI / Swagger UI is served at `/swagger-ui.html`; the spec is at `/api-docs`.
- Outbound port interfaces use Kotlin-idiomatic nullable returns (`Flow?`) instead of Java `Optional`.

### The Dependency Rule

> **The Domain and Application layers must never import from `adapter.*` or any Spring/JPA-specific type.**

Enforced by package structure:
- `core/domain/` — no imports from `application`, `adapter`, Spring, or JPA
- `core/port/` — imports only from `core/domain/` and `dto/`
- `application/` — imports only from `core/`, `dto/`, and `exception/`
- `adapter/inbound/web/` — imports from `core/port/inbound/` and `dto/`
- `adapter/outbound/persistence/` — imports from `core/port/outbound/` and `core/domain/`


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

Backend tests live in `backend/src/test/kotlin/com/factstore/`. Two kinds of tests are expected:

1. **Integration tests** (`@SpringBootTest` + `@Transactional`) — wires the full Spring context with H2 and tests end-to-end service behaviour. These live directly in `com.factstore` (e.g. `FlowServiceTest`).

2. **Unit tests** (no Spring context) — test a single service in complete isolation using an in-memory mock adapter (e.g. `InMemoryFlowRepository`). These live in `com.factstore.core` (e.g. `FlowServiceUnitTest`). This is the preferred style for new business-logic tests because they run instantly and require no database.

**Mock adapters** for driven ports live in `backend/src/test/kotlin/com/factstore/adapter/mock/`. To test a new service in isolation, create an `InMemory*Repository` that implements the corresponding `I*Repository` port, then instantiate the service directly in your test's `@BeforeEach`.

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

### Dependency Rule compliance
- [ ] No class in `core/domain/` or `core/port/` imports from `application/`, `adapter/`, or any Spring/JPA type.
- [ ] No class in `application/` imports from `adapter/`.
- [ ] `adapter/inbound/web/` controllers depend on inbound port **interfaces** (`IFlowService`, etc.), not concrete service classes.
- [ ] `adapter/outbound/persistence/` adapters depend on JPA repositories and implement outbound port **interfaces** (`IFlowRepository`, etc.).

### Where to add a new feature
1. **Add the outbound port first** — define the new data-access method in `core/port/outbound/I*Repository.kt`.
2. **Implement the adapter** — implement the method in the corresponding `*RepositoryAdapter` and `*RepositoryJpa` in `adapter/outbound/persistence/`.
3. **Add the inbound port** — define the new use case in `core/port/inbound/I*Service.kt`.
4. **Implement the service** — implement the method in `application/*Service.kt` (injecting only port interfaces, never concrete adapters).
5. **Add the web adapter** — expose the endpoint in `adapter/inbound/web/*Controller.kt` (using the inbound port interface).
6. **Write the unit test first** — test the service in isolation using a mock adapter in `src/test/.../adapter/mock/`.
7. **Add an integration test** if HTTP-level or database behaviour needs verification.

### General checks
- [ ] A failing test was written **before** the production code (Red step completed).
- [ ] All new behaviour is covered by at least one test.
- [ ] All existing tests still pass (`./gradlew test` and `npm run build`).
- [ ] No business logic has been placed in Controllers or Repositories.
- [ ] New endpoints follow the existing REST conventions and return appropriate HTTP status codes.
- [ ] New domain exceptions follow the existing exception pattern and are handled by `GlobalExceptionHandler`.
- [ ] New TypeScript types are added to `frontend/src/types/index.ts`.
- [ ] CI is green on the pull request.

## Shared agent skills

Shared skills live in [MaximumTrainer/agent-skills](https://github.com/MaximumTrainer/agent-skills). Before writing a new
skill, runbook or repeated procedure, check the catalogue - and send genuinely
general improvements back so the other repositories get them too.

```bash
python3 .claude/skills/skill-exchange/scripts/skills.py list
python3 .claude/skills/skill-exchange/scripts/skills.py status
```

See `.claude/skills/skill-exchange/` for the workflow.
