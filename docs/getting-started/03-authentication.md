# Part 3: Authentication

Factstore uses **API key authentication** for all API requests. Every request must include your API key in the `Authorization` header as a bearer token.

```bash
curl -H "Authorization: Bearer <your-api-key>" \
  "$BASE_URL/api/v1/flows"
```

---

## Service Accounts

A **service account** is a machine identity designed for non-human callers such as CI/CD pipelines and deployment scripts. Each service account has one or more API keys and is scoped to an organisation.

### Create a service account

```bash
curl -s -X POST "$BASE_URL/api/v1/service-accounts" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ci-pipeline",
    "description": "GitHub Actions deployment pipeline",
    "orgSlug": "my-org"
  }'
```

The response includes the service account's `id`. Use the `id` to create API keys.

### Create an API key for a service account

```bash
curl -s -X POST "$BASE_URL/api/v1/service-accounts/$SA_ID/api-keys" \
  -H "Content-Type: application/json" \
  -d '{ "name": "primary-key" }'
```

**Save the `key` value from the response immediately** — it is only shown once.

### List service accounts

```bash
curl -s "$BASE_URL/api/v1/service-accounts"
```

### Delete an API key (rotation)

To rotate a key with zero downtime:

1. Create a new key: `POST /api/v1/service-accounts/$SA_ID/api-keys`
2. Update all callers to use the new key value.
3. Delete the old key: `DELETE /api/v1/service-accounts/$SA_ID/api-keys/$KEY_ID`

---

## Personal API Keys

A **personal API key** is tied to a human user account and carries the same permissions as that user. Use personal keys for local development and testing; use service account keys for automated pipelines.

Personal keys are managed through the same API key endpoint scoped to your user's service account, or through the Factstore UI under **Settings → API Keys**.

---

## Using an API key

Include the key as a bearer token in every request:

```bash
export FACTSTORE_API_KEY="fs_live_xxxxxxxxxxxx"

curl -H "Authorization: Bearer $FACTSTORE_API_KEY" \
  "$BASE_URL/api/v1/flows"
```

---

Previous: [← Part 2: Setup & Access](./02-setup.md) | Next: [Part 4: Flows →](./04-flows.md)
