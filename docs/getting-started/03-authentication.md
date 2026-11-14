# Part 3: Authentication

Authentication is enforced: every API request must carry a credential, or it is refused with `401`. API clients send an **API key** as a bearer token in the `Authorization` header:

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v2/flows" | jq length
```

Until now `FACTSTORE_TOKEN` has been the bootstrap admin key from `.env` ([Part 2](./02-setup.md)). It exists for first use: create the identities and keys you need with it, then revoke it. People sign in to the web UI instead; see [docs/authentication.md](../authentication.md).

---

## Service Accounts

A **service account** is a machine identity for non-human callers such as CI/CD pipelines and deployment scripts. Each service account has one or more API keys.

### Create a service account

```bash
SA_ID=$(curl -s -X POST "$BASE_URL/api/v1/service-accounts" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ci-pipeline",
    "description": "GitHub Actions deployment pipeline"
  }' | jq -r .id)
echo "$SA_ID"
```

### Create an API key for the service account

A key is created with a scope **preset**. `CI_PIPELINE` lets a pipeline record trails, artifacts and attestations and run assertions:

```bash
CI_KEY=$(curl -s -X POST "$BASE_URL/api/v1/api-keys" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"ownerId\": \"$SA_ID\",
    \"ownerType\": \"SERVICE_ACCOUNT\",
    \"label\": \"primary-key\",
    \"preset\": \"CI_PIPELINE\",
    \"ttlDays\": 90
  }" | jq -r .plainTextKey)
```

**Store `plainTextKey` from the response immediately** (for example as a CI secret): it is only returned once. The response also carries the key's `id`, its `scopes` and `expiresAt`. `GET /api/v1/api-keys/scopes` lists every scope and preset.

### Use the key

```bash
curl -s -H "Authorization: Bearer $CI_KEY" "$BASE_URL/api/v2/flows" | jq length
```

### List service accounts

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/service-accounts" | jq '.[].name'
```

### Rotate and revoke

To rotate a key with zero downtime, call `POST /api/v1/api-keys/{id}/rotate`: it returns a new key and keeps the old one working for an overlap window, so you can update every caller before it stops. Revoke a key with `DELETE /api/v1/api-keys/{id}/revoke`.

---

## Personal API Keys

A **personal API key** belongs to a person and carries no more than that person's role. Use personal keys for local development; use service account keys for pipelines. Create one in the web UI under **Settings → API Keys**, or with `POST /api/v1/api-keys` and `"ownerType": "USER"`.

Scopes, presets, expiry and the full key lifecycle are in [docs/api-authorisation.md](../api-authorisation.md).

---

Previous: [← Part 2: Setup & Access](./02-setup.md) | Next: [Part 4: Flows →](./04-flows.md)
