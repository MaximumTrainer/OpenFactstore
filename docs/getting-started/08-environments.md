# Part 8: Environments

A **Factstore Environment** represents a runtime system — a Kubernetes cluster, a set of AWS Lambda functions, a Docker host, or any server that runs your built artifacts. Factstore records periodic **snapshots** of what is running in each environment, giving you a complete, time-ordered history.

This answers questions like:
- *"What is currently deployed in production?"*
- *"What was running in staging at 14:32 last Tuesday?"*
- *"When did version `v1.4.2` of the backend first appear in production?"*

---

## Register an environment

```bash
curl -s -X POST "$BASE_URL/api/v1/environments" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production",
    "type": "K8S",
    "description": "Production Kubernetes cluster"
  }'
```

Supported environment types: `K8S`, `DOCKER`, `S3`, `LAMBDA`, `ECS`, `GENERIC`

Store the returned `id` as `ENV_ID` for subsequent calls.

---

## Record a snapshot

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

---

## Get the latest snapshot

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/snapshots/latest"
```

---

## Environment diff & drift detection

Compare any two snapshots to identify unauthorised or unexpected changes:

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/diff?snapshotA=$SNAP_A&snapshotB=$SNAP_B"
```

Response identifies:
- **Added** — artifacts present in snapshot B but not A
- **Removed** — artifacts present in snapshot A but not B
- **Updated** — same artifact name but different SHA-256 digest

Drift reports are automatically created and made available at:

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/drift"
```

---

## Allow-listing third-party artifacts

Artifacts that are not tracked by Factstore (third-party images, approved base images) can be allow-listed to bypass policy checks for a specific environment.

### Add an allow-list entry

```bash
curl -s -X POST "$BASE_URL/api/v1/environments/$ENV_ID/allowlist" \
  -H "Content-Type: application/json" \
  -d '{
    "sha256": "sha256:abc123...",
    "justification": "Approved nginx base image — reviewed 2026-01-15",
    "expiresAt": "2027-01-15T00:00:00Z"
  }'
```

### List allow-list entries

```bash
curl -s "$BASE_URL/api/v1/environments/$ENV_ID/allowlist"
```

Allow-listed artifacts are automatically granted `ALLOWED` at the deployment gate, skipping all policy evaluation.

---

## Environment compliance

An environment's compliance state is determined by the [Policies](./09-policies.md) attached to it. An environment with no attached policies has an **unknown** compliance state.

---

Previous: [← Part 7: Attestations](./07-attestations.md) | Next: [Part 9: Policies →](./09-policies.md)
