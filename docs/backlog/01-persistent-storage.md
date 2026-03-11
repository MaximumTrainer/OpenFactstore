# 01 — Persistent Storage (PostgreSQL)

> **Status:** ✅ Existing — [GitHub Issue #12](https://github.com/MaximumTrainer/Factstore/issues/12)

## Summary

Replace the current H2 in-memory database with PostgreSQL for production-grade persistent storage. Add Flyway for database schema migrations, HikariCP for connection pooling, and Docker Compose for local development.

## Relevance to Financial Services Requirements

This feature directly supports the **Fact Store Schema** requirement from the financial services compliance scenario. PostgreSQL provides the relational storage backbone for structured fact records with relationships, as outlined in the SQL schema example:

```sql
CREATE TABLE facts (
    fact_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP DEFAULT NOW(),
    event_type TEXT NOT NULL,
    related_entity TEXT NOT NULL,
    status TEXT CHECK (status IN ('PASSED', 'FAILED', 'PENDING')),
    details JSONB,
    evidence_url TEXT
);
```

## Key Deliverables

- PostgreSQL driver and Flyway migrations
- Docker Compose for local development
- HikariCP connection pooling
- Environment variable configuration
- H2 retained for unit tests

## Acceptance Criteria

See [GitHub Issue #12](https://github.com/MaximumTrainer/Factstore/issues/12) for full acceptance criteria.
