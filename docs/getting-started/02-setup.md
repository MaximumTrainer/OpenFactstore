# Part 2: Setup & Access

Factstore runs as two Spring Boot services (Kotlin, PostgreSQL): a **command service** on port 8080 that takes every write, and a **query service** on port 8081 that serves reads from its own read model, fed by events over RabbitMQ. Docker Compose starts both, with their databases, RabbitMQ and Vault. The [user guide's architecture section](../user-guide.md#2-architecture) shows how they fit together.

> **Note:** Factstore ships a CLI (`cli/`). All examples in this guide are plain HTTP calls with `curl` and `jq`.

## Prerequisites

- **Docker** with Docker Compose
- `curl` and `jq`
- **Java 21+**, only to run the backend from source

## Running Factstore locally

<!-- docs-e2e:skip -->
```bash
git clone https://github.com/MaximumTrainer/OpenFactstore.git
cd OpenFactstore

# Once: a session secret, a bootstrap admin API key and the Prometheus scrape key, in .env
scripts/bootstrap-env.sh

docker compose up -d --build
```

| Service | URL |
|---|---|
| Command API (writes, and reads) | http://localhost:8080 |
| Query API (reads only) | http://localhost:8081 |

## Credentials and base URL

Authentication is enforced: a request without a credential is refused with `401`. `scripts/bootstrap-env.sh` wrote a bootstrap admin key to `.env`; use it for this guide, and see [Part 3](./03-authentication.md) for creating keys of your own. Every example assumes these variables, set from the repository root:

```bash
export BASE_URL=http://localhost:8080
export FACTSTORE_TOKEN="$(grep '^SECURITY_BOOTSTRAP_API_KEY=' .env | cut -d= -f2-)"
```

## Verifying the server is running

```bash
curl -s "$BASE_URL/api/v1/flows" -H "Authorization: Bearer $FACTSTORE_TOKEN" | jq length
# A number: 0 on a fresh instance
```

## Swagger / OpenAPI

Factstore publishes an interactive API explorer at:

```
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI spec is available at `http://localhost:8080/api-docs`. Both need a credential, like the rest of the API (sign in to the web UI first for Swagger UI):

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api-docs" | jq -r .info.title
```

The same document is committed as [`docs/openapi.json`](../openapi.json), and CI fails when it differs from the served API.

## Configuration

`docker-compose.yml` configures both services through environment variables; the full list is in the [user guide](../user-guide.md#environment-variables). The ones you are most likely to change:

| Variable | Default | Description |
|---|---|---|
| `SECURITY_ENFORCE_AUTH` | `true` | Refuse requests without a valid credential |
| `SECURITY_BOOTSTRAP_API_KEY` | from `.env` | Admin key for first use |
| `DB_HOST` / `DB_NAME` | per service | Each service has its own PostgreSQL database |

---

Previous: [← Part 1: Overview](./01-overview.md) | Next: [Part 3: Authentication →](./03-authentication.md)
