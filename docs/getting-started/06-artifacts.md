# Part 6: Artifacts

Artifacts are the outputs of your build process: Docker images, JAR files, binaries, archives. Factstore identifies every artifact by its **SHA-256 digest** — the record is tied to the exact bytes, not a name or a tag that can be reassigned.

## Reporting an artifact

After your build produces an artifact, report it to the Trail (`TRAIL_ID` from [Part 5](./05-trails.md)). Keep the digest in `DIGEST`; Part 7 asserts compliance for it:

```bash
DIGEST="sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f"

curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/artifacts" \
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"imageName\": \"my-org/backend\",
    \"imageTag\": \"v1.4.2\",
    \"sha256Digest\": \"$DIGEST\",
    \"registry\": \"ghcr.io\",
    \"reportedBy\": \"github-actions\"
  }" | jq .
```

**Response:**

```json
{
  "id": "c3d4e5f6-0000-0000-0000-000000000003",
  "trailId": "b2c3d4e5-0000-0000-0000-000000000002",
  "imageName": "my-org/backend",
  "imageTag": "v1.4.2",
  "sha256Digest": "sha256:0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f",
  "registry": "ghcr.io",
  "reportedBy": "github-actions",
  "createdAt": "2026-03-11T10:10:00Z"
}
```

### Artifact fields

| Field | Type | Required | Description |
|---|---|---|---|
| `imageName` | string | ✅ | Repository/image name (e.g. `my-org/backend`) |
| `imageTag` | string | ✅ | Version tag (e.g. `v1.4.2`, `latest`, git SHA) |
| `sha256Digest` | string | ✅ | `sha256:` followed by the 64 hex characters of the digest |
| `reportedBy` | string | ✅ | Who reported it, e.g. the CI system |
| `registry` | string | — | Registry the image was pushed to |
| `tags` | object | — | Free-form key/value labels |

## Computing the SHA-256 digest

### For a Docker image (by digest from registry)

<!-- docs-e2e:skip -->
```bash
docker inspect --format='{{index .RepoDigests 0}}' my-org/backend:v1.4.2
# Output: my-org/backend@sha256:0f53b5b9e7c266defe6...
```

### For a file

<!-- docs-e2e:skip -->
```bash
sha256sum ./backend.jar
# Output: 0f53b5b9e7c266defe6984deafe039b116295b2df4a409ba6288c403f2451a9f  backend.jar
```

### For an OCI image from a registry (without pulling)

<!-- docs-e2e:skip -->
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
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/trails/$TRAIL_ID/artifacts" | jq .
```

## Find artifacts by SHA-256

Search across all Trails for a specific artifact digest:

```bash
curl -s -H "Authorization: Bearer $FACTSTORE_TOKEN" "$BASE_URL/api/v1/artifacts?sha256=$DIGEST" | jq .
```

This is useful for answering: *"Which pipeline built this exact image?"* or *"Has this digest been seen before?"*

## Build provenance

Beyond the basic metadata, you can record an artifact's build provenance (builder identity, SLSA level, source repository and build configuration) with `POST /api/v1/trails/{trailId}/artifacts/{artifactId}/provenance`, check its signature with `POST /api/v1/trails/{trailId}/artifacts/{artifactId}/provenance/verify`, and read it back by digest with `GET /api/v1/artifacts/{sha256}/provenance`. To prove *which pipeline* built an artifact, record the CI provider's identity token as an attestation; see [OIDC Provenance Attestation](../user-guide.md#oidc-provenance-attestation).

---

Previous: [← Part 5: Trails](./05-trails.md) | Next: [Part 7: Attestations →](./07-attestations.md)
