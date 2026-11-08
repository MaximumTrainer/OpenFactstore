# FactstoreDbPoolSaturated

**Severity:** warn

## What fired

More than 90% of a service's HikariCP connections have been in use for 5 minutes. When the pool is exhausted, requests wait up to `connection-timeout` (30s) and then fail with a 5xx.

## First checks

1. `hikaricp_connections_pending{service="<service>"}`: are requests already waiting for a connection?
2. Is traffic up? Compare `sum(rate(http_server_requests_seconds_count{service="<service>"}[5m]))` with its usual level. A CI fleet starting a release train is a common cause.
3. Are queries slow? Check database CPU, locks and long-running statements (`pg_stat_activity`).
4. Is the database itself degraded (failover, storage)?

## Recovery

- Load: scale the service out, or raise `spring.datasource.hikari.maximum-pool-size` (default 20) if the database has headroom. The total across instances must stay below the database's `max_connections`.
- Slow queries: find and fix the statement; end runaway sessions if they are blocking others.
- Database degraded: resolve that first; the pool recovers on its own.
