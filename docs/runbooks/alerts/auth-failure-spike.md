# FactstoreAuthFailureSpike

**Severity:** warn

## What fired

More than one credential per second has been rejected for 10 minutes. The threshold is a starting point; tune it in `monitoring/alerts/factstore.yml` once normal traffic is known.

## First checks

1. Why are they rejected? `sum by (reason) (rate(factstore_auth_failures_total[10m]))`
   - `EXPIRED` or `REVOKED`: a pipeline is using a key that was rotated, revoked, or reached its TTL. Usually one pipeline retrying in a loop.
   - `UNKNOWN` or `MALFORMED` from many sources: possible credential stuffing.
2. The audit log has an `AUTH_FAILED` event per rejection, with the source IP, route and the credential's 12-character prefix (never the credential itself). Group by prefix and source IP to find the caller.
3. Is the rate limiter answering `429`? Repeated failures from one source or prefix are backed off automatically.

## Recovery

- A broken pipeline secret: issue a new key for its service account and update the pipeline's secret. See [`docs/api-authorisation.md`](../../api-authorisation.md).
- Credential stuffing: block the source at the edge (WAF, ingress allowlist), and confirm that no `UNKNOWN` prefix later succeeds.
