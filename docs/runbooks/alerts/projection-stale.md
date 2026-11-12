# FactstoreProjectionStale, FactstoreProjectionStopped

**Severity:** warn (stale), page (stopped)

## What fired

A region serves reads from its own read model, fed by the writer region ([ADR 0002](../../adr/0002-multi-region.md)).

- **Stale:** for ten minutes, events have been taking more than a minute to become readable in this region (`factstore_projection_delay_seconds`).
- **Stopped:** writes are succeeding, but this region has applied no event for fifteen minutes (`factstore_projection_last_applied_seconds`). Its reads are going stale.

Gates evaluated in that region see old evidence. They fail safe (missing evidence blocks), but pipelines may be blocked for no good reason.

## First checks

1. Is the region's query service consuming? Check its logs and the broker queue depth for its consumer.
2. Is the event feed flowing from the writer? Look for `FactstoreEventPublishFailing` on the command service ([event-publish-failing.md](event-publish-failing.md)).
3. Are events failing to apply? Look for `FactstoreProjectionFailure` ([projection-failure.md](projection-failure.md)).
4. After a writer failover, the region may still be pointed at the old writer's feed. Step 7 of the [failover runbook](../region-failover.md) repoints it.

## Recovery

Restore the feed. The projector is idempotent, so redelivered events are safe. Until the region catches up, consider taking it out of the read rotation so gates are evaluated elsewhere.
