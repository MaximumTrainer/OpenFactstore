---
title: "Feature: Environment Tracking & Snapshots"
labels: ["enhancement", "kosli-feature", "environment"]
---

## Summary

Add first-class support for tracking runtime environments (Kubernetes clusters, S3 buckets, Lambda functions, bare-metal servers) and recording periodic snapshots of their state. This directly mirrors Kosli's core _Environment_ concept: _"a history of snapshots for a runtime system over time"_.

## Motivation

Currently, factstore tracks *artifacts* and *attestations* but has no concept of a runtime environment. Operators cannot answer: _"What is currently running in production?"_ or _"What was running in staging last Tuesday?"_

## Requirements

### Data Model

- **Environment** entity: `id`, `name` (unique), `type` (K8S | S3 | LAMBDA | GENERIC), `description`, `createdAt`, `updatedAt`
- **EnvironmentSnapshot** entity: `id`, `environmentId`, `snapshotIndex` (monotonically increasing), `recordedAt`, `recordedBy`
- **SnapshotArtifact** (join): `snapshotId`, `artifactSha256`, `artifactName`, `artifactTag`, `instanceCount`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/environments` | Register a new environment |
| GET | `/api/v1/environments` | List all environments |
| GET | `/api/v1/environments/{id}` | Get environment details |
| PUT | `/api/v1/environments/{id}` | Update environment |
| DELETE | `/api/v1/environments/{id}` | Delete environment |
| POST | `/api/v1/environments/{id}/snapshots` | Record a new snapshot (list of running artifacts) |
| GET | `/api/v1/environments/{id}/snapshots` | List all snapshots for an environment |
| GET | `/api/v1/environments/{id}/snapshots/latest` | Get the most recent snapshot |
| GET | `/api/v1/environments/{id}/snapshots/{snapshotIndex}` | Get a specific snapshot by index |

### Query Capabilities

- Query what artifacts were running in an environment at any point in time
- Query which environments a given artifact (by SHA256) has ever been deployed to
- Timeline view of all snapshots for an environment

### Frontend

- New **Environments** page listing all tracked environments
- Per-environment snapshot history with timeline view
- Show current running artifacts per environment

## Acceptance Criteria

- [ ] Environment CRUD API is implemented and tested
- [ ] Snapshot recording API accepts a list of running artifacts
- [ ] Historical snapshot retrieval works correctly
- [ ] API is documented in OpenAPI/Swagger
- [ ] Unit tests cover environment service and snapshot logic
- [ ] Frontend Environments page shows current state and history
