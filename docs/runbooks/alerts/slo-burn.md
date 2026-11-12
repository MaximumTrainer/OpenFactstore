# FactstoreReadSloFastBurn, FactstoreReadSloSlowBurn, FactstoreWriteSloFastBurn, FactstoreWriteSloSlowBurn

**Severity:** page (fast burn), warn (slow burn)

## What fired

Requests are failing with 5xx fast enough to use up an availability error budget early ([ADR 0002](../../adr/0002-multi-region.md)):

| SLO | Objective | 30-day budget |
|---|---|---|
| `read-availability` (`GET`/`HEAD`) | 99.99% | 4.3 minutes |
| `write-availability` (`POST`/`PUT`/`PATCH`/`DELETE`) | 99.9% | 43 minutes |

A fast burn means the budget would be gone in about two days at the current rate; a slow burn, in about five.

## First checks

1. Which region? Every request metric carries `region`:
   ```promql
   sum by (region, service) (rate(http_server_requests_seconds_count{application="factstore", status=~"5.."}[5m]))
   ```
2. Which routes? Group the same query by `uri` and `status`.
3. Follow [high-5xx-ratio.md](high-5xx-ratio.md) for the failing service: logs by correlation id, database and broker health.

## Recovery

- **One read region failing:** take it out of the global load balancer. The other regions serve reads from their own replicas.
- **The writer region is down:** follow the [region failover runbook](../region-failover.md). Write availability allows for one failover a month.
- **Every region is failing** points at a shared dependency: Vault (event signing fails closed with `503`), the schema registry, or a bad release. Roll back the release first.
