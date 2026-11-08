# FactstoreHigh5xxRatio

**Severity:** page

## What fired

More than 1% of requests to one Factstore service returned a 5xx status, sustained for 5 minutes. The `service` label says whether it is the command (write) or query (read) side. CI pipelines recording facts, or deployment gates querying them, are failing.

## First checks

1. Is it one route or all of them? In Prometheus:
   `sum by (uri, status) (rate(http_server_requests_seconds_count{application="factstore", service="<service>", status=~"5.."}[5m]))`
2. Which errors? `sum by (type) (rate(factstore_errors_total{service="<service>", status="500"}[5m]))`. `Unexpected` means an unhandled exception; `IntegrityException` means [integrity-failure.md](integrity-failure.md) is the real problem.
3. Take a `correlationId` from a failing response, or from the `SYSTEM_ERROR` notification if a rule routes it, and find its log lines, which carry the exception and stack trace. See [README.md](README.md#finding-the-logs-for-a-failure).
4. Check the dependencies: `GET /actuator/health/readiness` reports the database and, in the prod profile, RabbitMQ. If the pool is exhausted, see [db-pool-saturated.md](db-pool-saturated.md).
5. Did a deployment just happen? Compare the start of the error rate with the rollout time.

## Recovery

- A bad deployment: roll back to the previous image.
- A dependency outage: restore it. The command service keeps committed facts in the outbox while the broker is down and relays them afterwards (see [event-publish-failing.md](event-publish-failing.md)); no manual replay is needed.
- An unhandled exception on one route: the stack trace under the correlation id says where. Open an issue with the correlation id and the log lines, not the request body if it holds evidence.
