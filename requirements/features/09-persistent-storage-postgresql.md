---
title: "Feature: Persistent Storage (PostgreSQL)"
labels: ["enhancement", "kosli-feature", "infrastructure", "database"]
---

## Summary

Replace the current H2 in-memory database with PostgreSQL for production-grade persistent storage. Add Flyway for database schema migrations, HikariCP for connection pooling, and Docker Compose for local development. This is a foundational infrastructure requirement — all data is currently lost on application restart.

## Motivation

Factstore currently uses H2 in-memory (`spring.datasource.url=jdbc:h2:mem:factstore`). This means:
- All data is lost when the application restarts
- No concurrent access from multiple application instances
- No production-grade durability guarantees
- Cannot support the audit log (Feature 05) or environment snapshots (Feature 01) at any meaningful scale

## Requirements

### Database Migration

- Replace H2 dependency with PostgreSQL driver (`org.postgresql:postgresql`)
- Add Flyway (`org.flywaydb:flyway-core`) for versioned schema migrations
- Create initial migration script (`V1__initial_schema.sql`) from current JPA entity definitions
- Keep H2 available for unit tests via test scope dependency and `@DataJpaTest` configuration

### Configuration

- Externalize DB config via environment variables:
  - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- Update `application.properties` to use these environment variables
- Add `application-test.properties` that configures H2 for unit tests
- Add `application-local.properties` for local Docker Compose setup

### Connection Pooling

- Configure HikariCP (bundled with Spring Boot) with sensible defaults:
  - `maximum-pool-size: 20`
  - `minimum-idle: 5`
  - `connection-timeout: 30000`
  - `idle-timeout: 600000`

### Docker Compose

Create `docker-compose.yml` in the repo root:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: factstore
      POSTGRES_USER: factstore
      POSTGRES_PASSWORD: factstore
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
  backend:
    build: ./backend
    depends_on: [postgres]
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: factstore
      DB_USERNAME: factstore
      DB_PASSWORD: factstore
    ports:
      - "8080:8080"
volumes:
  postgres_data:
```

### CI/CD Pipeline Update

- Update `.github/workflows/ci.yml` to spin up a PostgreSQL service container for integration tests
- Use GitHub Actions `services:` block with `postgres:16-alpine`

### Flyway Migrations

- All schema changes going forward must be implemented as numbered Flyway migration scripts
- Naming convention: `V{n}__{description}.sql`
- Migration scripts are immutable once committed

## Acceptance Criteria

- [ ] PostgreSQL driver added and H2 moved to test scope
- [ ] Flyway configured with `V1__initial_schema.sql` covering all existing entities
- [ ] `application.properties` uses environment variable substitution for DB config
- [ ] `application-test.properties` configures H2 for unit tests (no test regressions)
- [ ] `docker-compose.yml` created and tested locally
- [ ] HikariCP connection pool configured
- [ ] CI pipeline updated to use PostgreSQL service container
- [ ] README updated with PostgreSQL setup instructions
- [ ] All existing unit and integration tests pass with new configuration
