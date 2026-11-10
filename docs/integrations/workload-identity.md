# Workload identity for CI/CD runners

CI runners can authenticate with a short-lived workload identity instead of a static API key (#189). The runner sends a token its platform issues for each job, so there is no long-lived secret to store, leak, or rotate.

Accepted tokens, sent as `Authorization: Bearer <token>`:

- **GitHub Actions** OIDC tokens (`https://token.actions.githubusercontent.com`).
- **GitLab CI** ID tokens (`https://gitlab.com`).
- **SPIFFE JWT-SVIDs**, and tokens from any other issuer you add (for example a self-managed GitLab or a SPIRE OIDC discovery provider).

## What is checked

A token is accepted only when all of these hold:

1. Its issuer is trusted, and its **signature** verifies against that issuer's published keys (JWKS, cached and refreshed when the issuer rotates keys).
2. `aud` contains the configured audience, so a token minted for another service cannot be replayed here.
3. It has not expired (with a small clock skew allowance).
4. Its `sub` matches one of the trust patterns you configure. **No workload is trusted until you set these.**
5. If the request also sends `X-Spiffe-ID`, it names exactly the verified subject. The header is never accepted on its own, because anyone can set a header.

A refused token gets `401` with `WWW-Authenticate: Bearer realm="factstore"`. Each refusal is counted in `factstore_auth_failures_total{reason="WORKLOAD_IDENTITY"}` and audited as `AUTH_FAILED`.

An accepted workload holds only the workload scopes (by default `attestations:write`), and its actions are attributed to `workload:<sub>`, for example `workload:repo:my-org/service:ref:refs/heads/main`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `factstore.oidc.allowed-subjects` | empty (trust nothing) | Comma-separated `sub` patterns; `*` matches anything |
| `factstore.oidc.audience` | `factstore` | The audience pipelines must request |
| `factstore.oidc.extra-issuers` | empty | More issuers, as `<issuer>\|<jwks-uri>`, comma-separated |
| `factstore.oidc.workload-scopes` | `attestations:write` | Scopes a verified workload holds |
| `factstore.ingest.require-workload-identity` | `false` | When `true`, `POST /api/v1/commands/facts` refuses static API keys and signed-in people with `403`; only a workload identity may ingest |

Example: trust the `main` branch of one GitHub repository and one SPIRE service account.

```properties
factstore.oidc.allowed-subjects=repo:my-org/service:ref:refs/heads/main,spiffe://example.org/ns/ci/sa/pipeline-runner
factstore.oidc.extra-issuers=https://spire.example.org|https://spire.example.org/keys
```

Patterns match the whole subject. Prefer exact branches or environments over a bare `repo:my-org/*`. Any workflow in a trusted repository can request a token, including one on an unreviewed branch.

## GitHub Actions

```yaml
permissions:
  id-token: write
steps:
  - id: token
    run: |
      TOKEN=$(curl -sS -H "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
        "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=factstore" | jq -r .value)
      echo "::add-mask::$TOKEN"
      echo "token=$TOKEN" >> "$GITHUB_OUTPUT"
  - run: |
      curl -sS -X POST "$FACTSTORE_URL/api/v1/commands/facts" \
        -H "Authorization: Bearer ${{ steps.token.outputs.token }}" \
        -H "Content-Type: application/json" -d @fact.json
```

## GitLab CI

```yaml
publish-fact:
  id_tokens:
    FACTSTORE_TOKEN:
      aud: factstore
  script:
    - curl -sS -X POST "$FACTSTORE_URL/api/v1/commands/facts" -H "Authorization: Bearer $FACTSTORE_TOKEN" -H "Content-Type: application/json" -d @fact.json
```

## Retiring static keys

API keys still work for people and administration. To stop pipelines from ingesting with them, set `factstore.ingest.require-workload-identity=true` once every pipeline has moved to a workload identity. Other endpoints still accept keys, because the CLI and admin tooling depend on them.

## Not yet covered

- Trust patterns are global rather than per organisation, and a workload is not yet bound to an organisation (#218).
- `X-Signature` request signing is #190/#191.
