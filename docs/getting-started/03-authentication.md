# Part 3: Authentication

> 🚧 **This feature is not yet implemented.**
>
> Factstore currently has **no authentication** — all API endpoints are publicly accessible. This is intentional for early development but is unsuitable for any shared or production deployment.
>
> **Tracked in:** [Feature: Authentication & Service Accounts — Issue #34](https://github.com/MaximumTrainer/Factstore/issues/34) *(see issue for full requirements and acceptance criteria)*

---

## What is planned

Authentication for Factstore will follow the same model as Kosli:

### Service Accounts

A **service account** is a machine identity designed for non-human callers such as CI/CD pipelines and deployment scripts. Each service account has one or more API keys. Service accounts are scoped to an organisation and carry fine-grained permissions.

### Personal API Keys

A **personal API key** is tied to a human user account and carries the same permissions as that user. Use personal keys for local development and testing; use service account keys for automated pipelines.

### API key rotation

Zero-downtime rotation will be supported:

1. Generate a new key
2. Update all callers to use the new key
3. Delete the old key

---

## How authentication will work (once implemented)

### In API requests

Every request will require a bearer token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer <your-api-key>" \
  "$BASE_URL/api/v1/flows"
```

### Via environment variable (future CLI)

Once the [Factstore CLI](https://github.com/MaximumTrainer/Factstore/issues/33) is available:

```bash
export FACTSTORE_API_TOKEN=<your-api-key>
factstore list flows
```

---

## Current state

Until this feature is implemented, **no `Authorization` header is required**. All examples in this guide omit auth headers for clarity, and this will remain accurate until the feature ships.

If you are evaluating Factstore in a shared environment today, consider placing it behind a reverse proxy with network-level access controls.

---

Previous: [← Part 2: Setup & Access](./02-setup.md) | Next: [Part 4: Flows →](./04-flows.md)
