# Part 2: Setup & Access

Factstore is a Kotlin Spring Boot application backed by an H2 in-memory database (PostgreSQL support is on the roadmap — see [Issue #12](https://github.com/MaximumTrainer/Factstore/issues/12)).

> **No CLI yet.** Unlike Kosli, Factstore does not currently ship a command-line tool. All operations are plain HTTP calls. A Go-based CLI is planned — see [Issue #33](https://github.com/MaximumTrainer/Factstore/issues/33). All examples in this guide use `curl`.

## Prerequisites

- **Java 21+** (for running from source)
- **Docker** (optional, for containerised setup)
- `curl` or any HTTP client (Postman, httpie, etc.)

## Running Factstore locally

### From source

```bash
git clone https://github.com/MaximumTrainer/Factstore.git
cd Factstore/backend
./gradlew bootRun
```

The server starts on **http://localhost:8080** by default.

### Via Docker

```bash
docker build -t factstore-backend ./backend
docker run -p 8080:8080 factstore-backend
```

## Verifying the server is running

```bash
curl http://localhost:8080/api/v1/flows
# Expected: []  (empty list on a fresh instance)
```

## Swagger / OpenAPI

Factstore publishes an interactive API explorer at:

```
http://localhost:8080/swagger-ui/index.html
```

The raw OpenAPI spec is available at:

```
http://localhost:8080/v3/api-docs
```

Use the Swagger UI to explore all available endpoints, inspect request/response schemas, and try out calls directly in your browser.

## Configuration

The default configuration (`backend/src/main/resources/application.properties`) runs with an in-memory H2 database. Data is **lost on restart** until PostgreSQL support is implemented.

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:h2:mem:factstore` | In-memory database |

## Base URL

All API endpoints in this guide assume:

```
BASE_URL=http://localhost:8080
```

Set this variable in your shell to copy-paste the examples directly:

```bash
export BASE_URL=http://localhost:8080
```

## Authentication

> 🚧 **Not yet implemented.** Factstore currently has no authentication — all API endpoints are publicly accessible. Adding API key authentication is a planned feature. See [Part 3: Authentication](./03-authentication.md) and [Issue #XX](https://github.com/MaximumTrainer/Factstore/issues) for details.

---

Previous: [← Part 1: Overview](./01-overview.md) | Next: [Part 3: Authentication →](./03-authentication.md)
