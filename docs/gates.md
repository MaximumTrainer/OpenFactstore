# Deployment gates and risk-based fallback

`POST /api/v1/queries/gates/evaluate` answers one question: may this artifact be deployed to this environment? (#193)

```http
POST /api/v1/queries/gates/evaluate
Authorization: Bearer <key with assert:execute>
Content-Type: application/json

{
  "artifact_sha256": "sha256:<64 hex>",
  "target_environment": "prod-payments",
  "flow_id": "<uuid>",
  "required_policies": ["sast", "junit"]
}
```

```json
{
  "status": "REJECTED",
  "fallback_mode": "NONE",
  "decision_tree": {
    "environment": "prod-payments",
    "tier": "TIER1",
    "flow_id": "…",
    "trail_id": "…",
    "outcome": "EVALUATED",
    "reason": "…",
    "required_policies": ["sast", "junit"],
    "missing": ["sast"],
    "failed": []
  },
  "missing_attestations": ["sast"]
}
```

`required_policies` lists attestation types that must have passed, in addition to the flow's own requirements. The gate does not accept any other field. In particular it rejects `tier` with `400`: the tier is the environment's, not the caller's.

## Read path: caching and freshness

Gates are the critical read path, separate from the write path that CI agents load (#194). Writes go to `/api/v1/commands/**` on the command service; gate queries go to `/api/v1/queries/**`, which a query service can serve from its own read replica in every region (see [ADR 0002](adr/0002-multi-region.md)).

- **Cached decisions.** A decision made from the evidence is cached in-process for `factstore.gates.cache-ttl-seconds` (default 30). Any change to the read model clears the cache at once, and again when the change commits: a new attestation, a projected event, any entity write. A repeated query therefore costs nothing until something changes. Fallback decisions are never cached.
- **`data_as_of`.** Every decision says how fresh it is: `evaluated_at` (earlier than now when it came from the cache), `cached`, and `staleness_seconds`. Staleness is how far behind the writer this read model was when it last caught up, the same measure as `factstore_projection_delay_seconds`. It is 0 on an unsplit deployment, where the read model is the writer's.
- **`max_staleness_seconds`.** A gate request may set the oldest data it accepts. If this read model is staler than that, the gate does not give a stale answer: it falls back by the environment's risk tier, below, with `reason` saying the data was stale.

## Risk tiers

Each environment has a `tier`, set with `POST` or `PUT /api/v1/environments`:

| Tier | When the gate cannot evaluate | Use for |
|---|---|---|
| `TIER1` (**default**) | **fail closed**: `REJECTED` | Tier-1 financial services, anything regulated |
| `STANDARD` | **fail open**: `APPROVED` | low-risk services where blocking a release costs more than an unverified deploy |

"Cannot evaluate" means a dependency failed: the evidence database is unreachable, a transaction can't be started, or a query times out. In that case the gate still answers, deterministically, and:

- `fallback_mode` is `FAIL_OPEN` or `FAIL_CLOSED`, and `decision_tree.outcome` is `FALLBACK`, with the reason;
- a `GATE_FALLBACK` audit record is written (it is also streamed to the SIEM, #199), and its id is returned as `audit_alert_id`;
- `factstore_gate_fallbacks_total{mode}` is incremented. `FactstoreGateFailedOpen` pages and `FactstoreGateFailedClosed` warns ([runbook](runbooks/alerts/gate-fallback.md)).

If the environment itself can't be read, its tier is unknown, so the gate fails closed.

## When Factstore cannot be reached at all

If Factstore can't be reached at all, the server can't answer, so the CLI decides by the same rule:

```bash
factstore gate check --artifact sha256:<64 hex> --environment prod-payments --flow-id <uuid> [--require sast]
```

- When Factstore answers, its decision stands, including any fallback it applied itself.
- On every successful call the CLI remembers the environment's tier in `--state-dir` (default `~/.factstore`).
- If Factstore can't be reached (no connection, a timeout, or a `502`/`503`/`504` from a gateway in front of it), the CLI decides by the remembered tier. `STANDARD` fails open; `TIER1`, or a tier the CLI has never seen, fails closed. A refused credential (`401`/`403`) is not an outage: the command fails.
- Each such decision is kept in the state directory and reported on the next successful call (`POST /api/v1/commands/gates/fallback-reports`, idempotent by `report_id`). Factstore then records it as a `GATE_FALLBACK` audit record with `"source": "client"` and counts it in `factstore_gate_fallbacks_total{source="client"}`, which raises the same alerts as a server-side fallback.
- The command exits `0` when the deployment may proceed and `1` when it must not, and warns on standard error whenever it approved without evidence.

CI runners are usually ephemeral, so cache the state directory between runs. Otherwise every unreachable call finds no remembered tier and fails closed:

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.factstore
    key: factstore-gate-state-${{ github.repository }}
- run: factstore gate check --artifact "$DIGEST" --environment prod-payments --flow-id "$FLOW_ID"
```
