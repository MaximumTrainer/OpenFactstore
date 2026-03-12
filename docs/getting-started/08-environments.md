# Part 8: Environments

> 🚧 **This feature is not yet implemented.**
>
> Environment tracking is a planned feature for Factstore. This page describes what the feature will do, so you can understand where it fits in the overall model and plan your integration.
>
> **Tracked in:** [Feature: Environment Tracking & Snapshots — Issue #4](https://github.com/MaximumTrainer/Factstore/issues/4)

---

## What environments will do

A **Factstore Environment** represents a runtime system — a Kubernetes cluster, a set of AWS Lambda functions, a Docker host, or any server that runs your built artifacts. Factstore records periodic **snapshots** of what is running in each environment, giving you a complete, time-ordered history.

This answers questions like:
- *"What is currently deployed in production?"*
- *"What was running in staging at 14:32 last Tuesday?"*
- *"When did version `v1.4.2` of the backend first appear in production?"*

## Planned API

Once implemented, the environment API will look like this:

### Register an environment

```bash
curl -s -X POST "$BASE_URL/api/v1/environments" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production",
    "type": "K8S",
    "description": "Production Kubernetes cluster"
  }'
```

**Planned environment types:** `K8S`, `DOCKER`, `S3`, `LAMBDA`, `ECS`, `GENERIC`

### Record a snapshot

A snapshot is the list of artifacts running in the environment at a point in time. Only changed snapshots are stored — if nothing changed since the last snapshot, no new record is created.

```bash
curl -s -X POST "$BASE_URL/api/v1/environments/$ENV_ID/snapshots" \
  -H "Content-Type: application/json" \
  -d '{
    "artifacts": [
      {
        "sha256": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
        "name": "my-org/backend",
        "tag": "v1.4.2",
        "instanceCount": 3
      }
    ]
  }'
```

### Get the latest snapshot

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/snapshots/latest"
```

### Get a historical snapshot

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/snapshots/42"
```

Snapshots are indexed monotonically — snapshot `42` is the 42nd recorded state change in this environment.

## How environments connect to Flows and Trails

Once an artifact's SHA-256 digest appears in an environment snapshot, Factstore can link it back to the Trail that built it. This closes the loop between *what was built* and *what is running*.

## Environment compliance

An environment's compliance state is determined by the [Policies](./09-policies.md) attached to it. An environment with no attached policies has an **unknown** compliance state.

## Environment diff & drift detection

> 🚧 **Also not yet implemented.**
>
> A planned drift detection feature will compare any two snapshots (or a snapshot against an approved baseline) to identify unauthorised or unexpected changes.
>
> **Tracked in:** [Feature: Environment Diff & Drift Detection — Issue #9](https://github.com/MaximumTrainer/Factstore/issues/9)

### Planned diff API

```bash
# Compare snapshot 41 to snapshot 42
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/diff?from=41&to=42"
```

Response will identify:
- **Added** — artifacts present in snapshot 42 but not 41
- **Removed** — artifacts present in snapshot 41 but not 42
- **Updated** — same artifact name but different SHA-256 digest

---

Previous: [← Part 7: Attestations](./07-attestations.md) | Next: [Part 9: Policies →](./09-policies.md)
