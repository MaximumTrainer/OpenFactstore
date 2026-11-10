# Authentication and roles

How a person signs in to OpenFactstore, what their role lets them do, and how sessions behave.

Machine authentication — API keys, scopes, and enforcement defaults — is documented separately
in [security-hardening.md](./security-hardening.md); both resolve into the **same role model**
described below.

---

## The role model

One model, two kinds of credential. A **person** gets permissions from their role in the
organisation they are acting in; a **service account** gets them from the scopes on its API key.
Nothing else grants access.

Permissions are `resource:action` scopes:

| Role | Can |
|---|---|
| `VIEWER` | Read flows, trails, attestations, evidence, reports and the audit log |
| `MEMBER` | VIEWER, plus create trails, attestations and artifacts, run assertions, upload evidence, approve |
| `ADMIN` | MEMBER, plus create/edit/delete flows and policies, manage members, keys, service accounts and SSO |
| `SERVICE_ACCOUNT` | **Nothing from the role.** Capability comes solely from the key's scopes |

The roles are strictly nested — a higher role never loses a capability — and
`SERVICE_ACCOUNT` carrying nothing from its role is deliberate: it is what makes least
privilege possible for machine credentials.

`GET /api/v1/auth/me` returns the caller's permissions, and the web UI drives every navigation
and control-visibility decision from that response. **The UI is never the only check**: hiding a
control someone cannot use is a courtesy, and the server refuses the request regardless.

---

## Signing in

An unauthenticated visit to any UI route redirects to `/login`, preserving the intended
destination so sign-in lands where the user was going.

Two providers:

- **Organisation OIDC SSO** — enter the organisation slug; the browser is redirected to that
  organisation's identity provider.
- **GitHub OAuth** — available when the deployment has a GitHub OAuth app registered. A
  first-time GitHub user joins `security.github.default-org` as a `VIEWER`. Setup:
  [integrations/github-oauth.md](./integrations/github-oauth.md).
- **Development sign-in** — local development only; see [Local development](#local-development).

Organisation SSO setup, including the redirect URI to register with the IdP:
[integrations/oidc-sso.md](./integrations/oidc-sso.md).

### The OIDC flow

```
/login  →  GET /api/v1/organisations/{slug}/sso/login?redirectUri=…
        →  redirect to the IdP  (state + nonce + PKCE S256 challenge)
        →  IdP authenticates the person
        →  GET /api/v1/organisations/{slug}/sso/callback?code=…&state=…
        →  code exchanged (with the PKCE verifier)
        →  ID token verified, session issued, cookie set
```

Every one of those checks matters, so they are listed explicitly:

| Check | Why |
|---|---|
| **Signature**, against the provider's JWKS | Without it the token is just a string the caller chose. Keys are cached and refetched on an unknown `kid`, which handles provider key rotation. |
| `iss` | The token was minted by the provider we configured, not another one. |
| `aud` | It was minted *for us*, not for a different relying party. |
| `exp` / `nbf` | It is current. Clock skew tolerance is `sso.jwt.clock-skew-seconds` (default 60). |
| `nonce` | It belongs to **this** login attempt, so a token obtained elsewhere cannot be replayed into ours. |
| **PKCE** `code_verifier` | The party redeeming the authorization code is the one that started the flow, so an intercepted code is useless alone. |
| `state` | Single-use, bound to the organisation and the redirect URI, expiring after 10 minutes. |

> **What this replaced.** The previous implementation base64-decoded the ID token payload and
> trusted it, with a comment saying so. That made the authentication decision itself on
> unverified input: anyone able to reach the callback could present a self-made token naming
> any email address and be signed in as that person. TLS protects the transport; it says
> nothing about who minted the token.

### First sign-in

A federated user is provisioned just-in-time. Their role comes from the IdP group mapping in the
organisation's SSO configuration (`groupRoleMappings`), and where the user is in **no mapped
group the role is `VIEWER`** — signing in must not by itself confer write access. Group changes
at the IdP are applied on each sign-in.

---

## Sessions

A session is a **row**, not just a signed token. That is what makes the following possible at
all:

| Behaviour | Setting | Default |
|---|---|---|
| Token lifetime | `security.session.lifetime-seconds` | 1 hour |
| Absolute lifetime (refresh ceiling) | `security.session.absolute-lifetime-seconds` | 12 hours |
| Idle timeout | `security.session.idle-timeout-seconds` | 30 minutes |

The token carries only a subject and a session id (`sid`). **Role and organisation are not in
the token** — they are read per request, so a role change or an organisation switch takes effect
immediately rather than at the next sign-in.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/auth/me` | Identity, organisation, role, permissions, session expiry. `401` when unauthenticated, never an anonymous placeholder |
| `POST /api/v1/auth/logout` | Revokes the session server-side and clears the cookie. Works with an already-expired session, so a stale cookie can always be cleared |
| `POST /api/v1/auth/refresh` | Extends the session without returning to the IdP, bounded by the absolute lifetime |
| `GET /api/v1/auth/sessions` | The caller's own sessions, with the current one marked |
| `DELETE /api/v1/auth/sessions/{id}` | Sign out another of your own devices |
| `GET`/`DELETE /api/v1/auth/users/{id}/sessions` | List or revoke a user's sessions (`admin`) — the lever for a compromised account |
| `POST /api/v1/auth/organisation` | Switch the organisation the session acts in |

Revocation is effective on the **next request**: the token stays cryptographically valid until
it expires, but the session row says it is dead, and that row is what is consulted.

---

## How the browser holds the session

The session is an **`HttpOnly`, `SameSite=Strict` cookie** (`fs_session`), `Secure` except on a
plain-HTTP localhost request where a browser will not store a `Secure` cookie at all.

`HttpOnly` means JavaScript cannot read it, so an XSS bug cannot exfiltrate the session — which
is the trade the issue's open question asked about, resolved in favour of the cookie.

The cost of an ambient credential is CSRF, addressed without a token exchange:

- `SameSite=Strict` means the cookie is not sent on a cross-site request at all;
- a **cookie-authenticated mutating request must also carry `X-Factstore-Client`**. A cross-site
  form post cannot set a custom header, and CORS is deny-by-default, so it cannot be forged.

API clients send `Authorization: Bearer <token>` instead. A bearer token is not ambient — the
attacker's page cannot make the browser attach it — so no extra check applies to it.

---

## The signing secret has no default

`SSO_JWT_SECRET` must be set, at least 32 bytes, and not one of the known placeholders. The
application **refuses to start** when sign-in is reachable (SSO configured, or
`SECURITY_ENFORCE_AUTH=true`) and the secret is unusable:

```bash
SSO_JWT_SECRET=$(openssl rand -base64 48)
```

> **What this replaced.** The secret defaulted to `changeme-in-production` behind a startup
> `WARN`. A warning is not a control: anyone who knew the default could forge a session token
> for any user, and the deployment would look healthy.

A local instance with no SSO and no enforcement keeps working without a secret, and cannot issue
sessions either — which is the honest position rather than a silently forgeable one.
`security.session.allow-unconfigured-secret=true` overrides the refusal and logs loudly; it is
not for production.

### Local development

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun    # no IdP, no enforcement, no sign-in required
```

The `dev` profile turns enforcement off. That is deliberately low-friction and **not a
production configuration**: the API then accepts unauthenticated requests. Every other profile,
and the Docker Compose stack, enforces authentication. See
[security-hardening.md](./security-hardening.md) for the production posture.

To work **with** enforcement on, and to see the UI as a particular role, use **development
sign-in**. It needs no identity provider:

```bash
SPRING_PROFILES_ACTIVE=local SECURITY_ENFORCE_AUTH=true SSO_JWT_SECRET=$(openssl rand -base64 48) ./gradlew bootRun
```

The `local` and `dev` profiles switch it on (`security.dev-login.enabled=true`). `/login` then
shows a **Development sign-in** form: enter any email and pick a role (default `ADMIN`, in
organisation `dev`). It calls `POST /api/v1/auth/dev-login`, which issues an ordinary session
marked provider `DEV`, so everything after sign-in behaves exactly as in production.

Safeguards:

- Off by default. When off, `/api/v1/auth/dev-login` answers `404`.
- The application **refuses to start** with it on under the `prod` profile, and logs a warning
  whenever it is on.
- It answers only requests from the machine the backend runs on, unless
  `security.dev-login.loopback-only=false` (for a local Docker network).

---

## Attribution

Audit events name the actor:

| Credential | Recorded as |
|---|---|
| A signed-in person | their email |
| An API key | `api-key:<owner id>`, so the key's owner is on the record |
| Neither | `system` — and then it is genuinely true |

Sign-in, sign-out, failed sign-in, session revocation and role changes are audit events in their
own right: "who was in the system, when" is a question a compliance product has to answer.

Gate decisions, attestations and flow edits are attributed to whoever caused them, rather than
to `system` as they were before.

---

## Configuration reference

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `sso.jwt.secret` | `SSO_JWT_SECRET` | *(none)* | Session signing secret. No safe default |
| `sso.jwt.issuer` | — | `openfactstore` | `iss` on issued session tokens |
| `sso.jwt.clock-skew-seconds` | — | `60` | Tolerance on token time claims |
| `security.enforce-auth` | `SECURITY_ENFORCE_AUTH` | `true` (`false` in the `dev` profile) | Refuse unauthenticated requests |
| `security.session.lifetime-seconds` | — | `3600` | Session token lifetime |
| `security.session.absolute-lifetime-seconds` | — | `43200` | Ceiling refresh cannot pass |
| `security.session.idle-timeout-seconds` | — | `1800` | Idle timeout |
| `security.session.allow-unconfigured-secret` | — | `false` | Start anyway with an unusable secret. Not for production |
| `spring.security.oauth2.client.registration.github.client-id` | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` | *(none)* | Enables the GitHub sign-in option |
| `security.github.default-org` | `GITHUB_DEFAULT_ORG` | *(none)* | Organisation a first-time GitHub user joins, as `VIEWER` |
| `security.dev-login.enabled` | `SECURITY_DEV_LOGIN_ENABLED` | `false` (`true` in `local`/`dev`) | Development sign-in. Refused under `prod` |
| `security.dev-login.loopback-only` | `SECURITY_DEV_LOGIN_LOOPBACK_ONLY` | `true` | Development sign-in only from this machine |
