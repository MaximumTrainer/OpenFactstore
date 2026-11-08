# FactstoreEventPublishFailing

**Severity:** warn

## What fired

The command service has been failing to publish committed domain events to the event bus (RabbitMQ in the prod profile) for 15 minutes.

No facts are lost. Each committed event is recorded in the `event_outbox` table in the same transaction, and `EventOutboxRelay` re-publishes whatever is outstanding every 10 seconds (`factstore.events.outbox.relay-interval-ms`) once the broker is reachable. Until then the query side, and the deployment gates that read it, are behind the write side.

## First checks

1. Is the broker up? `GET /actuator/health/readiness` on the command service includes `rabbit` in the prod profile.
2. How far behind is the query side? On the command database:
   ```sql
   SELECT count(*), min(enqueued_at), max(attempts) FROM event_outbox;
   ```
   `last_error` on the oldest row says why its last attempt failed.
3. Have the broker's credentials or permissions changed? The error is in the command service's logs.

## Recovery

- Restore the broker. The relay catches up on its own, oldest event first; watch `event_outbox` drain to zero.
- If rows stay in the outbox while the broker is healthy, check the relay is running (it logs `Relayed N outstanding domain event(s)`) and that `factstore.scheduling.enabled` has not been set to `false`.
