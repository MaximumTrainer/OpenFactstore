# API authentication and authorisation

Machine access to the OpenFactstore API: how a credential is presented, what it may do, and
how it is managed.

Human sign-in is documented in [authentication.md](./authentication.md). Both resolve into the
**same role model**, described there.

---

## Presenting a credential

`Authorization: Bearer <key>` is the documented scheme for all clients.

| Scheme | Status |
|---|---|
| `Authorization: Bearer <key>` | **Recommended** |
| `X-API-Key: <key>` | Supported |
| `Authorization: ApiKey <key>` | Deprecated. Still accepted; logged as deprecated by key **prefix** only |

A key looks like `fsp_<64 hex>` (user-owned) or `fss_<64 hex>` (service account).

### When a credential is refused

A malformed, unknown, expired or revoked credential is rejected **immediately** with `401`,
`WWW-Authenticate: Bearer realm="factstore"`, and a problem body:

```json
{
  "error": "Unauthorized",
  "reason": "EXPIRED",
  "message": "The credential has expired",
  "credentialPrefix": "fss_a1b2c3d"
}
```

`reason` is one of `MISSING`, `MALFORMED`, `UNKNOWN`, `EXPIRED`, `REVOKED`. The messages
deliberately do **not** distinguish "no such key" from "wrong key", so a caller cannot use the
response to discover whether a key exists. Only the **prefix** is echoed — enough to identify
which credential failed, never enough to use it.

> **What this replaced.** The filter used to validate a key and, on failure, fall through and
> let the route decide. A wrong key therefore looked exactly like no key at all — and with
> enforcement off, like success.

### Rate limiting

Failed authentication backs off exponentially, counted separately **per source address and per
credential prefix**, returning `429` with `Retry-After`. A successful authentication clears the
counters, so a pipeline that fixes its credential is not left locked out.

Counting both ways is deliberate: one misconfigured runner should not lock out a whole NAT, and
one stolen prefix should not be rescued by rotating source addresses.

### Cost per request

Validation is cached on a SHA-256 of the credential, so BCrypt runs once per key per cache
window, not per request. The budget for the added latency of authenticating a request is
**1 ms at p50 and 5 ms at p99**.

`./gradlew authBenchmark` measures it (`AuthOverheadBenchmark`). It makes 5,000 authenticated
`GET /api/v1/flows/{id}` requests with a warm cache, interleaved with the same request made
anonymously, and writes `build/reports/auth-benchmark.json`. CI runs it and warns when either
figure is over budget. Measured on 2026-11-10 (Windows laptop, JDK 21, in-process MockMvc):

| | Anonymous | Authenticated | Overhead |
|---|---|---|---|
| p50 | 0.92 ms | 1.44 ms | **0.52 ms** |
| p99 | 2.41 ms | 3.73 ms | **1.32 ms** |

### Plaintext HTTP is refused

With enforcement on, a plaintext request from another machine is answered `426 Upgrade
Required` before any credential is read (#208, FR-10.1). A redirect would not help: the client
has already sent its key once. Loopback callers and health probes are allowed. Behind a proxy
that terminates TLS, set `server.forward-headers-strategy=framework` so `X-Forwarded-Proto:
https` marks the request secure. `security.transport.require-tls=false`
(`SECURITY_TRANSPORT_REQUIRE_TLS`) turns the check off for a private network that carries plain
HTTP between services, as the local Docker Compose stack does.

---

## Scopes

A key carries `resource:action` scopes, and its authorities are derived from them.

| Scope | Allows |
|---|---|
| `flows:read` / `flows:write` | Read / create, edit, archive, delete flows |
| `trails:read` / `trails:write` | Read / create trails |
| `attestations:write` | Record attestations — **the CI-pipeline scope** |
| `artifacts:write` | Register artifacts |
| `evidence:read` / `evidence:write` | Read / upload evidence |
| `assert:execute` | Run compliance assertions |
| `policies:read` / `policies:write` | Read / upload and delete policies |
| `approvals:write` | Grant approvals |
| `operator` | Read across organisations from a credential bound to none; each such read is audited as `CROSS_TENANT_READ` |
| `metrics:read` | Scrape `/actuator/prometheus`, and nothing else. What the Prometheus scrape key carries |
| `admin` | Everything, including key management, service accounts, organisation and SSO configuration |

`GET /api/v1/api-keys/scopes` returns this vocabulary and the presets, so a client need not
hard-code it.

`admin` is not a narrower privilege sitting alongside the others: a key holding it **grants every
permission**. That follows from what the word already means elsewhere — the `ADMIN` role holds
the full set, and a caller holding `admin` may mint a key carrying any scope it likes, so any
narrower reading is one API call from being irrelevant.

> **What this replaced.** `admin` derived exactly one authority, `SCOPE_admin`. So the bootstrap
> credential — the *only* credential a fresh enforced deployment has — could not create a flow:
> the first thing an operator tried with the credential they had just been handed returned
> `403`. It broke `dogfood.yml`, `verify-factstore.yml` and `terraform-verify.yml`, and no test
> caught it because those all used `@WithMockUser`, which grants authorities directly and never
> exercises scope derivation at all.

### Presets

| Preset | Scopes |
|---|---|
| `CI_PIPELINE` | `trails:write`, `attestations:write`, `artifacts:write`, `evidence:write`, `assert:execute`, `flows:read`, `trails:read` |
| `READ_ONLY` | `flows:read`, `trails:read` |

**A key created without scopes gets `READ_ONLY`, not full access.** That is the whole point:

> **What this replaced.** Every valid key granted exactly one authority, `ROLE_API_USER`,
> regardless of owner or purpose. A CI key that only needed to post attestations could delete
> flows, mint further keys, and read every organisation's evidence.

### No privilege escalation

A caller may only grant scopes it holds itself; requesting more is `403`. A caller holding
`admin` may grant anything. An unauthenticated caller — possible while enforcement is off — may
create only a read-only key.

---

## The authorisation matrix

| Operation | Requires |
|---|---|
| Read flows, trails, attestations, evidence, reports, audit log | a read scope |
| Create trails, attestations, artifacts; run assertions; upload evidence | the matching write scope |
| Create / edit / archive / delete / rename a flow | `flows:write` |
| Upload, edit or delete a policy | `policies:write` |
| Manage organisation members | `admin` |
| Manage service accounts and their keys | `admin` |
| Create, rotate or revoke an API key | `admin` |
| Configure SSO | `admin` |
| List or revoke another user's sessions | `admin` |

`EndpointAuthorisationMatrixTest` checks this against the annotations, so the table and the code
cannot drift: an endpoint on a matrixed controller either carries an `@PreAuthorize` requiring
the documented scope, or appears in an explicit allowlist with a stated reason. It also fails on
a **stale** allowlist entry, which is how an exemption outlives the endpoint it was written for
and starts silently covering a new one.

> Policy deletion previously required `hasAnyRole('ADMIN', 'MEMBER', 'API_USER')` — which let a
> `MEMBER` and *any* API key delete a policy. The check found it.

**Every endpoint**, not only those on the matrixed controllers, now states its rule.
`EveryEndpointIsAuthorisedTest` fails the build on any handler with neither `@PreAuthorize` nor an
explicit `@PublicEndpoint(reason)`, and the complete, generated endpoint-to-rule table is
[api-authorisation-matrix.md](api-authorisation-matrix.md). The rules added for that use
`@authz.has('<scope>')`, which keeps an unenforced instance working for anonymous callers while
holding any presented credential to its scopes. See the matrix page for how each rule reads.

---

## Managing keys

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/api-keys` | Create. Plain text returned **once** |
| `POST` | `/api/v1/api-keys/{id}/rotate` | Rotate, with an overlap window |
| `GET` | `/api/v1/api-keys/owners/{ownerId}` | List (never returns key material) |
| `GET` | `/api/v1/api-keys/scopes` | The scope vocabulary and presets |
| `DELETE` | `/api/v1/api-keys/{id}/revoke` | Revoke |

```http
POST /api/v1/api-keys
{
  "ownerId": "uuid",
  "ownerType": "SERVICE_ACCOUNT",
  "label": "GitHub Actions — payments",
  "preset": "CI_PIPELINE",
  "orgSlug": "acme",
  "ttlDays": 90
}
```

### TTL

A maximum TTL is enforced at creation (`security.api-key.max-ttl-days`, default 90), and
**omitting `ttlDays` uses the default rather than creating a key that never expires**. A
non-expiring key requires `neverExpires: true` *and*
`security.api-key.allow-non-expiring=true`, and is logged as the explicit override it is.

A key within `security.api-key.expiry-warning-days` (default 7) of expiry is flagged on the key
resource (`expiringSoon`, `daysUntilExpiry`) **and** on every response to a request made with
it, via `X-Factstore-Credential-Warning` — so a pipeline hears about it before it breaks.

### Rotation

```http
POST /api/v1/api-keys/{id}/rotate
{ "overlapHours": 24 }
```

Returns a replacement with the same scopes and organisation. **The previous key keeps working
until the overlap window closes**, so a pipeline can pick up the new value on its next run
rather than failing on its next call. Both keys work during the window; the old one then stops.

### Revocation

Effective on the **next request**. Successful validations are cached briefly
(`security.api-key.cache-ttl-seconds`, default 60) to avoid a BCrypt comparison per call —
tens of milliseconds of CPU each, which for a pipeline in a loop is the dominant cost of the
request — but revoking a key drops its cached entries immediately, and the key row is
re-checked even on a cache hit. The cache is keyed on a SHA-256 of the credential, never the
credential itself.

---

## Tenant binding

A key can be bound to one organisation (`orgSlug`). It authenticates *into* that organisation,
and the binding travels with the request for scoping.

A resource belonging to another organisation returns **`404`, not `403`** — a `403` confirms the
resource exists, which is enough to enumerate another tenant's data.

This is enforced (`TenantScope`) for a caller bound to an organisation, whether a key with
`orgSlug` or a signed-in user acting in their active organisation:

| Surface | Behaviour |
|---|---|
| `GET /api/v1/flows/{id}`, `/api/v1/trails/{id}`, `/api/v1/evidence/{sha256}` and the v2 equivalents | another organisation's resource is `404` |
| `GET /api/v1/flows`, `/api/v1/trails`, `/api/v2/flows`, `/api/v2/trails`, and a trail list for another organisation's flow | only the caller's organisation |
| `GET /api/v1/search`, `POST /api/v1/search` | only the caller's organisation, whatever `orgSlug` the request names |
| `GET /api/v1/dashboard/stats` | counts only the caller's organisation |
| Environments (and their snapshots, baselines, drift, audit CSV), policies and their versions, approvals, compliance assessments | another organisation's is `404`; lists show only the caller's (#207) |
| `GET /api/v1/artifacts?sha256=`, `/api/v1/artifacts/search`, `GET /api/v1/gate/results` | only the caller's organisation (#207) |
| `GET /api/v1/audit`, `/api/v1/audit/{id}`, a trail's audit events | only events tied to the caller's organisation's trails or environments (#207) |

The same lookups back attestation recording and flow edits and deletes, so a bound key cannot
write to another organisation's trail either: it gets the same `404`.

A trail without its own `orgSlug` belongs to its flow's organisation, and evidence to its
attestation's, then its trail's. A resource with no organisation at all is visible only to
**unbound** callers. An unbound credential (no `orgSlug`, such as the bootstrap admin key) sees
every organisation, which is how every deployment behaved before tenant binding.

An approval, gate result or audit event takes the organisation of its trail or environment. An
audit event tied to neither (a key created, a failed sign-in) is visible only to unbound callers.

### Cross-organisation reads (FR-5.4)

When a caller that presented a credential but is bound to no organisation reads another
organisation's resources, the request is audited once as `CROSS_TENANT_READ`, naming the
organisations, method, path and status. Anonymous callers on an unenforced instance are not
audited.

`security.tenancy.unbound-requires-operator` (default `false`) decides whether such reads need
the `operator` scope. With `true`, an unbound credential without `operator` sees only resources
that belong to no organisation, and one with it reads across organisations, audited. `admin`
includes `operator`, so the bootstrap admin key keeps working. #209 turns this on with the
enforcement flip.

Not yet scoped: OPA decisions, deployment policies, the regulatory report generator and the
remaining reports.

---

## First run

A fresh deployment with `SECURITY_ENFORCE_AUTH=true` and no admin credential cannot be
administered — there would be no way to authenticate in order to create the credential that
would let you authenticate. So on first start OpenFactstore creates a **bootstrap admin key**
and writes it to the log once, with instructions to rotate it:

```
================ OpenFactstore bootstrap credential ================
   key:     fsp_…
   expires: 7 days
```

A secret in a startup log is not ideal. It is visible, short-lived, and much better than a
well-known default. Use it to create your real admin identity, revoke it, and set
`security.bootstrap.enabled=false`.

With bootstrapping disabled, enforcement on, and no admin credential, the application **fails
to start** with an actionable message rather than starting an unusable service.

### Supplying the credential by configuration

An unattended environment cannot scrape a value out of a log, so the credential can also be
given directly:

```bash
SECURITY_BOOTSTRAP_API_KEY=fsp_<64 hex>
```

On start, an active admin key with exactly that value is ensured — idempotently, so a restart
does not accumulate keys. A value that is not shaped like a key (wrong prefix, too short to
produce a usable lookup prefix) **fails startup** rather than creating a credential that can
never authenticate.

This applies **whatever `enforce-auth` is set to**, and that matters more than it first appears:
authorisation rules are always live, so creating a flow or uploading a policy needs a credential
even in a permissive deployment. It is why `dogfood.yml`, `verify-factstore.yml` and
`terraform-verify.yml` seed a key into their transient instances, and why `infra`'s
`factstore_token` is required rather than optional.

A credential supplied this way is known to anyone who can read that configuration. Use it for
development and CI; for production, prefer the generated credential and rotate it.

### The Prometheus scrape key

`SECURITY_BOOTSTRAP_METRICS_API_KEY` seeds a second key the same way, carrying only
`metrics:read` (365-day TTL by default, `SECURITY_BOOTSTRAP_METRICS_API_KEY_TTL_DAYS`). With
enforcement on, `/actuator/prometheus` requires `metrics:read` and the rest of `/actuator`
(other than health and info) requires `admin`. The scrape key can read nothing else: it cannot
list flows or reach `/actuator/env`. `monitoring/prometheus.yml` presents it from
`monitoring/secrets/prometheus-scrape-key`.

Each service seeds configured keys into its own database, so the command and query services
both accept the same bootstrap and scrape keys. A key created through the API exists only in
the command service's database.

### A local stack with Docker Compose

```bash
scripts/bootstrap-env.sh
docker compose up -d
```

The script generates a strong `SSO_JWT_SECRET`, a bootstrap admin key and the scrape key into a
git-ignored `.env`, and writes the scrape key file for Prometheus. It refuses to overwrite an
existing `.env` without `--force`. Enforcement stays off until `.env` sets
`SECURITY_ENFORCE_AUTH=true`.

---

## Rolling out enforcement

`security.enforce-auth` still defaults to **`false`**, deliberately, following the rollout in
#155 §5: scopes, the matrix, rotation and auditing ship first, then warn mode, and the default
flips in a clearly marked release.

### Warn mode

```yaml
security:
  warn-mode: true
```

Authenticate and authorise as normal, but on failure **log what would have been rejected and
allow the request**. Each one is counted as
`factstore_warn_mode_refusals_total{source="credential"|"rule"}`: a missing, bad or expired
credential, or an endpoint rule the caller does not meet. In warn mode an anonymous caller is
counted too, with or without enforcement, so this works as the pre-flight check before the
flip. A deployment is ready when
`sum(increase(factstore_warn_mode_refusals_total[7d]))` reaches zero. Endpoint rules written as `@authz...` log `WARN-MODE: would refuse <caller>
lacking <scope>; allowing`. This is how you find every unauthenticated caller before switching over,
instead of discovering them as an outage.

### Then

```yaml
security:
  enforce-auth: true
```

Which also:

- restricts `/actuator/**` to health and info (metrics, env and loggers require a credential);
- stops serving Swagger UI, `/api-docs` and the H2 console unauthenticated;
- sets HSTS;
- refuses plaintext HTTP from other machines with `426` (unless `security.transport.require-tls=false`);
- refuses a wildcard CORS origin outright.

Roll back with `SECURITY_ENFORCE_AUTH=false`.

---

## Auditing

| Event | Recorded |
|---|---|
| `AUTH_FAILED` | Reason, credential **prefix**, source IP, method, path |
| `API_KEY_CREATED` | Actor, key prefix, owner, scopes, TTL |
| `API_KEY_ROTATED` | Actor, both key ids, when the old key stops working |
| `API_KEY_REVOKED` | Actor, key prefix, owner |

**No audit entry or log line contains key material** — only the 12-character prefix. An audit
log that contains working credentials is a liability, not a control, and there is a test
asserting a created key's secret appears in no audit payload.

---

## Configuration reference

| Property | Default | Meaning |
|---|---|---|
| `security.enforce-auth` | `false` | Refuse unauthenticated requests |
| `security.warn-mode` | `false` | Log what would be rejected, allow it |
| `security.api-key.max-ttl-days` | `90` | Maximum TTL at creation |
| `security.api-key.default-ttl-days` | `90` | TTL when none is given |
| `security.api-key.allow-non-expiring` | `false` | Permit keys with no expiry |
| `security.api-key.rotation-overlap-hours` | `24` | How long a rotated key keeps working |
| `security.api-key.expiry-warning-days` | `7` | When to start warning |
| `security.api-key.cache-ttl-seconds` | `60` | Validation cache TTL |
| `security.api-key.rate-limit.enabled` | `true` | Rate-limit failed authentication |
| `security.api-key.rate-limit.max-failures` | `5` | Failures before backing off |
| `security.api-key.rate-limit.max-backoff-seconds` | `300` | Backoff ceiling |
| `security.bootstrap.enabled` | `true` | Create a first-run admin credential |
| `security.bootstrap.admin-email` | `admin@localhost` | Owner of the bootstrap credential |
| `security.bootstrap.api-key` | *(none)* | Admin credential supplied by configuration, for CI and development |
| `security.bootstrap.ttl-days` | `7` | Bootstrap credential lifetime |
| `security.cors.allowed-origins` | *(none)* | Comma-separated allowlist. Deny by default |

---

## Recommended setup for a pipeline

One **service account and one key per pipeline**, scoped `CI_PIPELINE`, bound to the
organisation, with a TTL and a rotation schedule. Then a leaked key is bounded in what it can
do, whose it is, and how long it lasts — which is the entire point of scoping.

See [ci-integration.md](./ci-integration.md) for the pipeline patterns.
