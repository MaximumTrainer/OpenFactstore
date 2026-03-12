# Contract Testing with Pact

This document describes how API contract testing is implemented in the Factstore repository using [Pact](https://docs.pact.io/).

## Overview

Factstore uses **consumer-driven contract testing** to verify that the Backend API (Provider) always satisfies the expectations of its consumers:

| Consumer | Location | Contract File |
|----------|----------|---------------|
| `factstore-frontend` | `frontend/` | `pacts/factstore-frontend-factstore-backend.json` |
| `factstore-cli` | `cli/` | `pacts/factstore-cli-factstore-backend.json` |

The Backend (`factstore-backend`) is the **Provider**. It verifies the generated pact files on every CI build.

## How It Works

```
Consumer (Frontend / CLI)          Provider (Backend)
─────────────────────────          ─────────────────
 1. Write consumer tests            3. Download pact files
    using @pact-foundation/pact        from CI artifacts
    ↓                                  ↓
 2. Tests generate pact files       4. Run ./gradlew contractTest
    describing request/response         which starts the backend and
    expectations                        verifies each interaction
    ↓                                  ↓
    Upload pact files as            5. Build fails if any
    CI artifacts                       interaction is not satisfied
```

## Running Contract Tests Locally

### Step 1: Generate consumer pact files

From the **Frontend**:
```bash
cd frontend
npm ci
npm run test:pact
```

From the **CLI**:
```bash
cd cli
npm ci
npm run test:pact
```

Both commands generate pact files into the top-level `pacts/` directory.

### Step 2: Run provider verification

```bash
cd backend
./gradlew contractTest
```

This starts the full Spring Boot application and verifies every interaction in the `../pacts/*.json` files.

> **Note:** The `pacts/` directory must contain the generated pact files before running `contractTest`. Run the consumer tests first.

## CI/CD Integration

The CI pipeline (`.github/workflows/ci.yml`) has three contract-testing jobs that run on every push and pull request:

| Job | Runs after | What it does |
|-----|-----------|--------------|
| `frontend-contract` | — | Runs frontend consumer tests, uploads `pacts-frontend` artifact |
| `cli-contract` | — | Runs CLI consumer tests, uploads `pacts-cli` artifact |
| `backend-contract` | `frontend-contract`, `cli-contract` | Downloads both pact artifacts, runs `./gradlew contractTest` |

If any consumer interaction is not satisfied by the backend, the `backend-contract` job fails and **the pull request cannot be merged**.

## Adding a New API Interaction

### Consumer side (Frontend or CLI)

1. Add a new `test()` block in the relevant pact spec file:
   - Frontend: `frontend/src/pact/consumer.pact.spec.ts`
   - CLI: `cli/src/pact/cli.consumer.pact.spec.ts`

2. Use `PactV3` interaction builders:
   ```typescript
   await provider
     .given('some provider state')
     .uponReceiving('a descriptive interaction name')
     .withRequest({ method: 'GET', path: '/api/v1/your-endpoint' })
     .willRespondWith({
       status: 200,
       body: like({ field: like('value') }),
     })
     .executeTest(async (mockServer) => {
       // Call the actual API function with mockServer.url as base URL
       const response = await axios.get(`${mockServer.url}/api/v1/your-endpoint`)
       expect(response.status).toBe(200)
     })
   ```

3. Run `npm run test:pact` to regenerate the pact file.

### Provider side (Backend)

1. If you introduced a new **provider state** (the `given()` string), add a corresponding `@State` method in `PactProviderVerificationTest.kt`:
   ```kotlin
   @State("some provider state")
   fun setupSomeState() {
       // Set up the H2 database using autowired JPA repositories
       flowRepository.deleteAll()
       flowRepository.save(Flow(name = "example", description = "..."))
   }
   ```

2. Run `./gradlew contractTest` to verify the new interaction passes.

## Modifying an Existing Interaction

1. Update the consumer test in the relevant `*.spec.ts` file.
2. Run `npm run test:pact` to regenerate the pact file with the updated expectation.
3. If the backend response shape changes, update the provider implementation accordingly.
4. If a new provider state is needed, add a `@State` method in `PactProviderVerificationTest.kt`.
5. Run `./gradlew contractTest` to confirm the backend satisfies the updated contract.

## Key Files

| File | Purpose |
|------|---------|
| `frontend/src/pact/consumer.pact.spec.ts` | Frontend consumer contract tests |
| `frontend/vitest.config.ts` | Vitest configuration for pact tests |
| `cli/src/pact/cli.consumer.pact.spec.ts` | CLI consumer contract tests |
| `cli/vitest.config.ts` | Vitest configuration for CLI pact tests |
| `backend/src/test/kotlin/com/factstore/pact/PactProviderVerificationTest.kt` | Backend provider verification test |
| `pacts/` | Generated pact files (created by consumer tests) |
| `.github/workflows/ci.yml` | CI pipeline including contract testing jobs |
