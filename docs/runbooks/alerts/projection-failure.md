# FactstoreProjectionFailure

**Severity:** warn

## What fired

The query service received a domain event it could not apply to the read model. The event was not acknowledged as a success and forgotten: it is recorded, with its payload, in the `projection_failures` table of the query database. Queries for the affected aggregate may return stale or missing data.

## First checks

1. List the failures:
   ```sql
   SELECT sequence_number, event_type, aggregate_id, reason, failed_at
   FROM projection_failures ORDER BY failed_at DESC LIMIT 50;
   ```
2. `reason` says whether the event type was unknown or the payload unreadable.
   - **Unknown event type** almost always means the command service runs a newer version than the query service. Check both image tags.
   - **Unreadable payload** means a serialisation change between versions, or corruption. Compare the payload with the row of the same `sequence_number` in the command database's `domain_events`. If they differ, go to [integrity-failure.md](integrity-failure.md).

## Recovery

- Version skew: roll the query service forward to the command service's version.
- Then replay the failed events from the command service's event store into the read model. The projector is idempotent, so replaying an event that did apply is safe.
- Once replayed and verified, archive the `projection_failures` rows rather than deleting them; they are part of the audit record.
