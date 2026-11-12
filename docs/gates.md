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

If Factstore can't be reached at all, the server can't answer. The client has to apply the same rule: fetch the environment's tier in advance (`GET /api/v1/environments`), and when the gate call fails to connect, fail open for `STANDARD` or closed for `TIER1`. Record the fallback, and post it once Factstore is reachable again. **This client-side fallback is not yet in the Go CLI or the GitHub Action** (follow-up to #193).
