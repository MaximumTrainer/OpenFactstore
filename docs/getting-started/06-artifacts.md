# Part 6: Artifacts

Artifacts are the outputs of your build process: Docker images, JAR files, binaries, archives. Factstore identifies every artifact by its **SHA-256 digest** — the record is tied to the exact bytes, not a name or a tag that can be reassigned.

## Reporting an artifact

After your build produces an artifact, report it to the Trail:

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/artifacts" \
  -H "Content-Type: application/json" \
  -d '{
    "imageName": "my-org/backend",
    "imageTag": "v1.4.2",
    "sha256Digest": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
    "buildUrl": "https://github.com/my-org/backend/actions/runs/12345678",
    "commitUrl": "https://github.com/my-org/backend/commit/e67f2f2b121f9325ebf166b7b3c707f73cb48b14"
  }'
```

**Response:**

```json
{
  "id": "c3d4e5f6-0000-0000-0000-000000000003",
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "imageName": "my-org/backend",
  "imageTag": "v1.4.2",
  "sha256Digest": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "buildUrl": "https://github.com/my-org/backend/actions/runs/12345678",
  "commitUrl": "https://github.com/my-org/backend/commit/e67f2f2b121f9325ebf166b7b3c707f73cb48b14",
  "createdAt": "2026-03-11T10:10:00Z"
}
```

### Artifact fields

| Field | Type | Required | Description |
|---|---|---|---|
| `imageName` | string | ✅ | Repository/image name (e.g. `my-org/backend`) |
| `imageTag` | string | ✅ | Version tag (e.g. `v1.4.2`, `latest`, git SHA) |
| `sha256Digest` | string | ✅ | Full SHA-256 digest prefixed with `sha256:` |
| `buildUrl` | string | — | URL to the CI build run that produced this artifact |
| `commitUrl` | string | — | URL to the source commit this artifact was built from |

## Computing the SHA-256 digest

### For a Docker image (by digest from registry)

```bash
docker inspect --format='{{index .RepoDigests 0}}' my-org/backend:v1.4.2
# Output: my-org/backend@sha256:0f53b5b9e7c266defe6...
```

### For a file

```bash
sha256sum ./backend.jar
# Output: 0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f  backend.jar
```

### For an OCI image from a registry (without pulling)

```bash
docker manifest inspect my-org/backend:v1.4.2 \
  --verbose | jq '.Descriptor.digest'
```

## Why SHA-256 matters

Because Factstore uses the digest as the artifact identity, the same image pulled on two different machines at two different times will always produce the same fingerprint. This means:

- **Tamper detection** — any change to the artifact changes the digest and breaks the record
- **Environment correlation** — when Factstore sees this digest in an environment snapshot, it can link it back to the Trail that built it (once [Environments](./08-environments.md) are implemented)
- **Cross-flow traceability** — the same artifact can appear in multiple Trails across multiple Flows

## List artifacts for a Trail

```bash
curl -s "$BASE_URL/api/v1/trails/$TRAIL_ID/artifacts"
```

## Find artifacts by SHA-256

Search across all Trails for a specific artifact digest:

```bash
curl -s "$BASE_URL/api/v1/artifacts?sha256=sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f"
```

This is useful for answering: *"Which pipeline built this exact image?"* or *"Has this digest been seen before?"*

## Build provenance

> 🚧 **Extended build provenance is not yet implemented.**
>
> Factstore currently records basic artifact metadata. A planned extension will capture full build provenance: builder identity, SLSA level, source repository URI, build configuration URI, and a cryptographic signature over provenance fields.
>
> **Tracked in:** [Feature: Artifact Build Provenance](https://github.com/MaximumTrainer/Factstore/issues/5)

---

Previous: [← Part 5: Trails](./05-trails.md) | Next: [Part 7: Attestations →](./07-attestations.md)
