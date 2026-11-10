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

## Source signatures

Every fact must be signed by the CI runner that sends it (#187). The runner signs the **exact request body bytes** with an Ed25519 key, then sends:

| Header | Value |
|---|---|
| `X-Signature` | the 64-byte Ed25519 signature, base64url (standard base64 is accepted too) |
| `X-Signature-Key-Id` | the id the key was registered under |

The signature is checked after validation and before anything is stored, against the trusted signing-key registry:

| `401` `error` | Meaning |
|---|---|
| `signature_missing` | either header is absent |
| `signature_malformed` | `X-Signature` is not a base64 Ed25519 signature |
| `unknown_signing_key` | the key id is not registered |
| `signing_key_revoked` | the key has been revoked |
| `signature_invalid` | the signature does not verify over the body, for example because the body changed after signing |

Refusals are counted in `factstore.ingest.denials{reason=<error>}`. An accepted fact keeps its key id, its signature and the exact body that was signed, both in the `facts` row and in its `FactIngested` event, so the signature can be checked again at any time.

Signing a fact with OpenSSL:

```bash
openssl genpkey -algorithm ed25519 -out runner.pem
openssl pkey -in runner.pem -pubout -out runner.pub.pem      # register this
openssl pkeyutl -sign -inkey runner.pem -rawin -in fact.json | basenc --base64url -w0
```

### The signing-key registry

Administrators manage it (all need `admin`):

| Request | Does |
|---|---|
| `POST /api/v1/commands/signing-keys` `{"keyId", "publicKey": "<PEM>"}` | registers an Ed25519 key: `201`, `400` if it is not Ed25519, `409` if the id is taken |
| `POST /api/v1/commands/signing-keys/{keyId}/revoke` | revokes it; facts signed with it are refused from then on |
| `GET /api/v1/queries/signing-keys` | lists keys and their status |

Registration and revocation are audited (`SIGNING_KEY_REGISTERED`, `SIGNING_KEY_REVOKED`). Keys are never deleted. **To rotate**, register the new key under a new id, move runners to it, then revoke the old one; both verify in between.

`factstore.ingest.require-signature` (default `true`) can be set to `false` while runners are being moved to signing. Unsigned facts are then accepted, but a signature that is sent is still verified.

## Identity lineage

Each fact is bound, by the server, to the identity verified when it arrived (#191). The request body cannot carry an identity: an `identity` field is an undefined envelope field and is refused with `invalid_envelope`.

| `identity.kind` | Sender | Recorded |
|---|---|---|
| `workload` | a verified OIDC token or SPIFFE JWT-SVID ([workload identity](integrations/workload-identity.md)) | `issuer`, `subject`, `spiffeId`, `repository`, `runId`, `runAttempt`, `actor`, `workflowRef`, `ref`, `sha` |
| `credential` | a static API key or a signed-in person | `subject`: `api-key:<owner id>` or the person's email |

GitHub Actions claims map directly (`repository`, `run_id`, `run_attempt`, `actor`, `workflow_ref`, `ref`, `sha`). GitLab CI's map as `project_path` → `repository`, `pipeline_id` → `runId`, `user_login` → `actor`, `ci_config_ref_uri` → `workflowRef`.

The identity is stored with the fact and carried in its `FactIngested` event. Nothing updates it. Facts ingested before this was added show `kind: "unrecorded"`.

`GET /api/v1/queries/facts/{fact_id}` (needs `trails:read`) returns the fact with its identity.

## Not yet covered

- Facts are not yet scoped to an organisation; see #218.
- Signing keys are not yet bound to a workload or service, so any registered key may sign any fact. Binding a key to the identity that may use it is a follow-up.
- Masking (#198) must happen on the runner, before signing, so the stored body is the signed body.
- The server-set identity (#191) is stored and evented but is not part of the signed bytes. Storage-side signing and hash chaining (#184, #188) must include it.
