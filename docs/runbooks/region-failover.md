# Runbook: failing writes over to another region

Factstore writes go to one region; reads are served in every region ([ADR 0002](../adr/0002-multi-region.md)). This runbook moves the writer to a standby region when the writer region is lost, and back again.

**Budget:** writes target 99.9% availability, which allows 43 minutes a month. Practise this procedure until it takes well under that.

## Before you need it

- The standby region runs the same release, with `FACTSTORE_CQRS_ROLE=command` deployed but scaled to zero, or with traffic not routed to it.
- Its command database is a streaming replica of the writer's (`domain_events`, `ledger_checkpoints`, `event_chain_head`, `facts` and the rest), so promoting it loses at most the replication lag.
- It can reach the event-signing key in Vault Transit (`transit/sign/factstore-events`). Either the Vault cluster spans both regions, or the standby uses a performance replica of it. Without the key the standby cannot write. It fails closed with `503` and does not write unsigned records (#184).
- The global load balancer or DNS routes write paths (`POST`, `PUT`, `PATCH` and `DELETE` on `/api/**`) to a single origin that you can switch.

## Failing over

1. **Confirm the writer is down.** `FactstoreWriteSloFastBurn` is firing, and the writer region's health endpoint and database are unreachable. Do not fail over for a partial outage you can fix in place inside the budget.
2. **Fence the old writer.** Make sure it can't accept writes if it comes back: remove it from the write route and scale its command service to zero. Two writers would fork the chain.
3. **Promote the standby database** to primary (`pg_ctl promote`, or your managed database's promote action).
4. **Start the standby command service** and check `GET /actuator/health/readiness`.
5. **Route writes to the standby.**
6. **Verify the chain** across the cut-over:

   ```http
   POST /api/v1/admin/events/verify
   {"from": <last checkpoint's last_sequence>, "to": <current head>}
   ```

   `valid: true` confirms that no record was lost or altered by promotion. Then checkpoint (`POST /api/v1/admin/ledger/checkpoints`) and check that the new root is published.
7. **Point read regions' projections at the new writer.** Query services consume the event feed; repoint the broker or replication source. `FactstoreProjectionStopped` clears once events flow again.

## Failing back

Rebuild the old region's database as a replica of the new writer, then repeat the steps above in the other direction during a quiet period.

## If records were lost

If promotion lost the newest records (asynchronous replication), the chain head no longer matches what pipelines saw. Their receipts (`receipt_hash`, #186) name records that no longer exist. Verification shows it: `firstFailure` reports the missing newest record. Ask the affected pipelines to re-send. Ingestion is idempotent by `fact_id`, so this is safe.
