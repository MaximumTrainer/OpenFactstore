# FactstoreGateFailedOpen, FactstoreGateFailedClosed

**Severity:** page (failed open), warn (failed closed)

## What fired

A deployment gate (`POST /api/v1/queries/gates/evaluate`) could not evaluate the evidence, because a dependency such as the evidence database was unreachable, so it decided by the target environment's risk tier (#193, [docs/gates.md](../../gates.md)):

- **Failed open:** a `STANDARD` environment's deployment was **approved without evidence**. Those deployments need review.
- **Failed closed:** a `TIER1` environment's deployment was **blocked**. Releases are stopped until the gate can evaluate again.

Every fallback decision writes a `GATE_FALLBACK` audit record. Its id is the `audit_alert_id` the caller received.

## First checks

1. Find the decisions:
   `GET /api/v1/audit?eventType=GATE_FALLBACK` lists them, each with the environment, artifact, flow and reason.
2. Fix the cause. It's usually the database ([db-pool-saturated.md](db-pool-saturated.md), [high-5xx-ratio.md](high-5xx-ratio.md)).

## After recovery

- **Failed open:** re-evaluate each listed artifact now that the gate works. Anything that comes back `REJECTED` was deployed without the required evidence; roll it back, or record a justified override.
- **Failed closed:** pipelines can retry.
