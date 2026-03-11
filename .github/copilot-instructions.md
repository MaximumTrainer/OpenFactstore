# Copilot Instructions for Factstore

## Project Overview

Factstore is a Supply Chain Compliance Fact Store — a full-stack web application for tracking and verifying that software artifacts (container images) meet predefined security and quality requirements before deployment. It uses a Kotlin/Spring Boot backend with a Vue 3/TypeScript frontend.

## Tech Stack

### Backend
- **Language:** Kotlin 2.0 on Java 21
- **Framework:** Spring Boot 3.2.5 with Spring Data JPA
- **Database:** H2 (in-memory)
- **Build tool:** Gradle with Kotlin DSL
- **Testing:** JUnit 5 + Mockito-Kotlin
- **API docs:** Springdoc OpenAPI 2.5.0

### Frontend
- **Framework:** Vue 3 (Composition API with `<script setup>`)
- **Language:** TypeScript 5.4
- **Build tool:** Vite 5
- **Styling:** Tailwind CSS 3.4
- **HTTP client:** Axios
- **State management:** Pinia
- **E2E testing:** Playwright

## Prerequisites

- Java 21 (Eclipse Temurin)
- Node.js 20 with npm

## Build & Test Commands

### Backend
```bash
cd backend
./gradlew build          # Compile, test, and produce JAR
./gradlew test           # Run unit tests only
./gradlew bootRun        # Start dev server on port 8080
```

### Frontend
```bash
cd frontend
npm ci                   # Install dependencies
npm run build            # Type-check and build for production
npm run dev              # Start Vite dev server on port 5173
npm run test:e2e         # Run Playwright end-to-end tests
```

## Architecture

The backend follows **Hexagonal Architecture** (Ports and Adapters). Dependencies always point inward — adapters depend on ports, ports depend on the domain.

### Package Layout
```
com.factstore/
├── core/domain/           # Business entities (Flow, Trail, Artifact, Attestation, EvidenceFile)
├── core/port/inbound/     # Driving port interfaces (IFlowService, IAssertService, …)
├── core/port/outbound/    # Driven port interfaces (IFlowRepository, ITrailRepository, …)
├── application/           # Use-case implementations (FlowService, AssertService, …)
├── adapter/inbound/web/   # REST controllers
├── adapter/outbound/persistence/  # JPA repository adapters
├── dto/                   # Request/response DTOs (all in Dtos.kt)
├── exception/             # Custom exceptions and global error handler
└── config/                # CORS and OpenAPI configuration
```

### Domain Model
```
Flow ──< Trail ──< Artifact
               └──< Attestation ──> EvidenceFile
```

### Frontend Layout
```
frontend/src/
├── views/        # Page-level Vue components
├── components/   # Reusable UI components (NavBar, StatusBadge)
├── api/          # Axios client modules per resource
├── router/       # Vue Router configuration
└── types/        # Shared TypeScript interfaces mirroring backend DTOs
```

## Coding Conventions

### Backend (Kotlin)
- Use Kotlin idioms: null safety (`?.`, `?:`), data classes, extension functions.
- Service classes implement inbound port interfaces (e.g., `FlowService : IFlowService`).
- Annotate services with `@Service` and `@Transactional`.
- Controllers use `@RestController` with `@RequestMapping("/api/v1/...")`.
- Use SLF4J for logging via `LoggerFactory.getLogger`.
- Name test functions using backtick syntax (e.g., `` `should create a flow successfully` ``).
- Throw custom exceptions (`NotFoundException`, `ConflictException`) — never return nulls for missing resources.
- DTOs are defined in a single `Dtos.kt` file; use `toResponse()` extension functions for entity-to-DTO conversion.

### Frontend (Vue 3 + TypeScript)
- Use Composition API with `<script setup lang="ts">`.
- Use Tailwind CSS utility classes for styling.
- API modules in `src/api/` export typed async functions wrapping Axios calls.
- Shared TypeScript interfaces live in `src/types/index.ts`.
- Use `ref()` and `reactive()` for component state; prefer `ref()` for primitives.

## Key Rules

- **Dependency rule:** Domain and application layers must never import from `adapter` or any external framework.
- **All REST endpoints** are under the `/api/v1` base path.
- **Frontend proxies** API calls to the backend via Vite's dev server proxy (`/api` → `http://localhost:8080`).
- **UUIDs** are used as primary keys for all entities.
- **Timestamps** use `java.time.Instant` in the backend.
