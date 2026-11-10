# Upgrading: authentication is enforced by default

From this release, OpenFactstore refuses unauthenticated requests unless told otherwise (#209, #155). Previously `SECURITY_ENFORCE_AUTH` defaulted to `false`, and every endpoint answered anyone who could reach it.

**Rollback, at any time:** set `SECURITY_ENFORCE_AUTH=false` and restart. No data changes in either direction.

## What changes when you upgrade

| Before | After |
|---|---|
| Any caller could read and write | Every request needs a credential: an API key (`Authorization: Bearer <key>`) or a signed-in session |
| An unauthenticated request was answered | `401` with `WWW-Authenticate: Bearer realm="factstore"` |
| A key bound to no organisation read every organisation | It needs the `operator` scope to read across organisations; without it, it sees only resources that belong to no organisation. Each such read is audited as `CROSS_TENANT_READ`. `admin` includes `operator` |
| Plaintext HTTP from any client was accepted | Plaintext HTTP from another machine gets `426 Upgrade Required` (see below) |
| `/actuator/prometheus` and `/actuator/env` were open | `/actuator/prometheus` needs `metrics:read`; the rest of `/actuator` (except health and info) needs `admin` |

## Before you upgrade

1. **Run one release in warn mode.** Set `SECURITY_WARN_MODE=true` on your current version. Nothing is refused; every request enforcement would refuse is logged as `WARN-MODE: would refuse …` and counted.
2. **Watch the count reach zero.** In Prometheus:
   `sum(increase(factstore_warn_mode_refusals_total[7d]))`
   `source="credential"` means a missing, wrong or expired key; `source="rule"` means a key without the scope the endpoint needs.
3. **Re-mint pipeline keys made before #206.** The `CI_PIPELINE` preset gained `flows:read` and `trails:read`. Keys minted with the older preset cannot make the CLI's read calls once enforcement is on. Rotate them (`POST /api/v1/api-keys/{id}/rotate`) or mint new ones with `"preset": "CI_PIPELINE"`.
4. **Give every pipeline its own key.** One service account and one key per pipeline, with the `CI_PIPELINE` preset. See [docs/ci-integration.md](../ci-integration.md#authenticating-a-pipeline).
5. **Give Prometheus its scrape key.** Set `SECURITY_BOOTSTRAP_METRICS_API_KEY` on the backend and put the same value in the file `monitoring/prometheus.yml` reads (`monitoring/secrets/prometheus-scrape-key`). `scripts/bootstrap-env.sh` does both for Docker Compose.
6. **Check transport.** With enforcement on, plaintext HTTP from another machine is refused with `426`:
   - Behind an ingress or proxy that terminates TLS, set `server.forward-headers-strategy=framework` so its `X-Forwarded-Proto: https` is honoured.
   - Services that call each other over plain HTTP on a trusted private network, including a Kubernetes Prometheus scraping pods, need TLS, or `SECURITY_TRANSPORT_REQUIRE_TLS=false` on that network.
   - Health probes are always answered.
7. **Bound or operator keys for cross-organisation tools.** A key with no `orgSlug` and no `operator` scope now sees only resources with no organisation. Bind tools to their organisation, or give a genuine cross-organisation tool the `operator` scope. To keep the previous behaviour while you migrate, set `SECURITY_TENANCY_UNBOUND_REQUIRES_OPERATOR=false`.

## First start of a new deployment

With enforcement on and no admin credential, the service creates a bootstrap admin key and writes it to the log once. Use it to create your real admin identity, then revoke it. To supply one instead, set `SECURITY_BOOTSTRAP_API_KEY`. See [docs/api-authorisation.md](../api-authorisation.md#first-run).

Local development keeps a low-friction path: the `dev` profile sets `SECURITY_ENFORCE_AUTH=false`, and the `local` and `dev` profiles offer development sign-in (see [docs/authentication.md](../authentication.md#local-development)).
