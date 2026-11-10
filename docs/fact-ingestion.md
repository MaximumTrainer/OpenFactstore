# Fact ingestion and strict ingress validation

`POST /api/v1/commands/facts` accepts a fact only when it strictly conforms to the schema it names (#196). A fact that does not conform is refused with `400` before anything is written, so a malformed or poison-pill payload never reaches the fact table or the event stream.

## Request

Needs `attestations:write`.

```http
POST /api/v1/commands/facts
Content-Type: application/json

{
  "fact_id": "6f1c2a8e-4b0d-4f6e-9a51-2d3c4e5f6a7b",
  "subject": { "sha256": "<64 hex characters>", "repository": "org/service" },
  "governance": { "schema_ref": "urn:schema:junit-summary:v1" },
  "tool": { "tool": "junit", "passed": 120, "failed": 0 }
}
```

## Validation, in order

1. **Envelope.** `fact_id` is a UUID; `subject.sha256` is 64 hexadecimal characters; `subject.repository`, if present, is a string; `governance.schema_ref` looks like `urn:schema:<name>:v<version>`; `tool` is an object. Fields the envelope does not define are refused too.
2. **Schema exists.** `schema_ref` must be published in the [schema registry](schema-registry.md).
3. **Payload conforms.** `tool` is validated against that schema (JSON Schema 2020-12). Whether extra properties are allowed is up to the schema: publish it with `"additionalProperties": false` to refuse them.

Every violation found at a stage is reported, not only the first.

## Responses

| Status | When | Body |
|---|---|---|
| `202 Accepted` | the fact conforms and was stored and appended as a `FactIngested` event | `fact_id`, `schema_ref`, `ingested_at` |
| `400 Bad Request` | the body is not JSON | the standard error body |
| `400 Bad Request` | a rule above is broken | `error`, `schemaRef`, `violations`, `correlationId` |
| `403 Forbidden` | the caller does not hold `attestations:write` | |
| `409 Conflict` | this `fact_id` was already ingested | |

`error` is one of:

| `error` | Meaning |
|---|---|
| `invalid_envelope` | step 1 failed |
| `unknown_schema` | step 2 failed |
| `schema_nonconformant` | step 3 failed |

Each violation is `{ "path": <JSON Pointer into the request body>, "message": ... }`, for example:

```json
{
  "error": "schema_nonconformant",
  "schemaRef": "urn:schema:junit-summary:v1",
  "violations": [
    { "path": "/tool/passed", "message": "/passed: string found, integer expected" }
  ],
  "correlationId": "..."
}
```

## Not yet covered

- Request signatures (`X-Signature`) and workload identity headers (`X-Spiffe-ID`) from the specification are not verified here yet; they belong to #190 and #191. When they are, validation stays first, so unvalidated data is never signature-checked or stored.
- Facts are not yet scoped to an organisation; see #218.
