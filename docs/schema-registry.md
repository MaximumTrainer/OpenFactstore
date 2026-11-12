# Schema registry

Every fact declares the schema its payload follows, and the registry holds those schemas (#195). A schema is a JSON Schema (draft 2020-12) document, published as a numbered version and named by its URN:

```
urn:schema:<name>:v<version>        e.g. urn:schema:junit-results:v2
```

A fact carries that URN in `governance.schema_ref`. The compliance spec also mentions a `schema_version` header field; `governance.schema_ref` is the single name used, and it carries both the schema and its version.

## Publishing

```http
PUT /api/v1/commands/schemas/{name}/v{version}
Authorization: Bearer <admin key>
Content-Type: application/json

{ "$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", ... }
```

| Response | When |
|---|---|
| `201 Created` | the version is new and was published |
| `200 OK` | this exact version and content were already published (safe to retry) |
| `409 Conflict` | the version is already published with different content: published versions are immutable |
| `409 Conflict` | the version breaks compatibility with the previous one; the message lists each breaking change |
| `400 Bad Request` | the body is not a valid JSON Schema, the name is malformed, or the previous version is missing |
| `403 Forbidden` | the caller does not hold `admin` |

Names are lower-case letters, digits, `.`, `_` and `-`. Versions start at 1 and are published in order. Each publication is audited as `SCHEMA_PUBLISHED`.

## Reading

| Request | Needs | Returns |
|---|---|---|
| `GET /api/v1/queries/schemas/{name}/v{version}` | `policies:read` | that version: `ref`, `name`, `version`, `schema`, `createdBy`, `createdAt` |
| `GET /api/v1/queries/schemas/{name}` | `policies:read` | every published version, oldest first |

## What counts as a breaking change

A new version must accept every payload the previous version accepted, so a CI runner that has not upgraded keeps working.

| Breaking | Compatible |
|---|---|
| removing a property | adding an optional property |
| making a property required | dropping a requirement |
| changing or narrowing a `type` | widening a type (for example `integer` to `number`) |
| removing an `enum` value, or adding an `enum` to an unrestricted field | adding an `enum` value |
| adding or changing a `pattern` or `format` | removing a `pattern` or `format` |
| closing `additionalProperties` | opening `additionalProperties` |

These rules are `SchemaCompatibility`, which #197's CI check is to reuse. To make a breaking change, publish it under a new schema name.

## Scope

Schemas are global, not per organisation: one registry serves every tenant.

## Schemas are specified in the repository

Fact schemas are developed spec-first (#197). The source of truth is `schemas/<name>/v<n>.json` in this repository, and the registry is a published copy of it.

- **On every pull request**, the `Schema and API compatibility` CI job runs `./gradlew schemaSpecCheck` against the base branch. It fails when a file is misnamed, a schema is not valid JSON Schema 2020-12, versions have a gap, a new version breaks compatibility with the previous one, or a published version was edited or deleted. "Breaking" means exactly what the registry answers `409` for on publish (the table above); both use `SchemaCompatibility`.
- **After a merge**, `scripts/publish-schemas.sh` publishes every version to a Factstore. Publishing is idempotent.
- **The HTTP API is specified too.** `docs/openapi.json` is the committed OpenAPI document. `OpenApiSnapshotTest` fails if it differs from the served API, and the same CI job runs `oasdiff breaking` on it against the base branch. To regenerate it after an intended change, run `./gradlew test --tests '*OpenApiSnapshotTest' -Dfactstore.update-openapi=true`.

An intended breaking API change is accepted by adding it, with the reason, to `docs/openapi-accepted-breaking.txt`, so the acceptance is reviewed in the pull request. Value sets that grow with the product, such as audit event types, are published as `x-extensible-enum`, so adding a value is not a breaking change.

To change a schema, add `schemas/<name>/v<n+1>.json` and never edit `v<n>`. To make a breaking change, start a new schema name.
