---
title: "Feature: Artifact Build Provenance"
labels: ["enhancement", "kosli-feature", "provenance", "security"]
---

## Summary

Extend artifact tracking with tamper-proof build provenance metadata. Every artifact should record cryptographically verifiable information about *how* it was built: the builder identity, build tools, source commit, and build timestamp. This aligns with Kosli's _Artifact Provenance_ feature: _"cryptographic fingerprinting to record a tamper-proof identity for every artifact"_.

## Motivation

Factstore currently records an artifact's SHA256 digest and basic metadata, but does not capture *build provenance*. Without provenance, an operator cannot verify: _"Was this binary built from the expected source code using approved tooling?"_ This is a critical gap for software supply chain security (SLSA compliance).

## Requirements

### Data Model

Extend the **Artifact** entity with a **BuildProvenance** sub-entity:

- `builderId` — identity of the build system (e.g., GitHub Actions runner ID)
- `builderType` — type of builder (`GITHUB_ACTIONS | JENKINS | CIRCLE_CI | GENERIC`)
- `buildConfigUri` — URI of the build configuration file (e.g., `.github/workflows/build.yml`)
- `sourceRepositoryUri` — URI of the source repository
- `sourceCommitSha` — git commit SHA the artifact was built from (already on Trail but duplicated here for provenance)
- `buildStartedOn` / `buildFinishedOn` — ISO-8601 timestamps
- `provenanceSignature` — optional ECDSA/RSA signature over provenance fields for tamper detection
- `slaLevel` — SLSA provenance level (`L0 | L1 | L2 | L3`)

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | Record build provenance for an artifact |
| GET | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | Retrieve build provenance |
| GET | `/api/v1/artifacts/{sha256}/provenance` | Get provenance by artifact SHA256 |

### Provenance Verification

- Endpoint to verify the provenance signature of an artifact
- Flag artifacts as `PROVENANCE_VERIFIED`, `PROVENANCE_UNVERIFIED`, or `NO_PROVENANCE`
- Include provenance status in compliance assertion results

### SLSA Compatibility

- Support ingesting SLSA provenance attestations (JSON format)
- Map SLSA fields to internal BuildProvenance model

### Frontend

- Show build provenance details in the Trail Detail view
- Visual indicator for provenance status (verified/unverified)
- SLSA level badge on artifact cards

## Acceptance Criteria

- [ ] BuildProvenance entity is created and linked to Artifact
- [ ] Provenance recording and retrieval API is implemented
- [ ] Provenance signature verification is implemented
- [ ] Compliance assertion includes provenance status
- [ ] Unit tests cover provenance service logic
- [ ] OpenAPI documentation is updated
- [ ] Frontend shows provenance details in Trail Detail view
