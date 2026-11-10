# Runbook: event-signing keys

Every record Factstore writes to its event log (`domain_events`) is signed by an Ed25519 key held in **Vault Transit** (#184). Factstore can ask Vault to sign, and can read the public keys. It never sees the private key, which is not in its configuration, its environment or its database.

This runbook covers creating, rotating and disabling that key, and verifying the records it signed.

## How it fits together

| Setting | Production value |
|---|---|
| `factstore.event-signing.provider` (`EVENT_SIGNING_PROVIDER`) | `vault-transit`, the default under the `prod` profile |
| `factstore.event-signing.vault.key-name` (`EVENT_SIGNING_VAULT_KEY`) | `factstore-events` |
| `vault.enabled` and its connection settings | as for the rest of the Vault integration |

The `local` provider signs with an in-memory key that is lost on restart, so records it signed can't be verified after a restart. It exists for development and tests, and Factstore refuses to start with it under the `prod` profile.

Each record stores its `signing_key_id` (`<key-name>:v<version>`), `signature_algorithm`, `canonical_version`, `prev_hash` and `record_hash`. Records written before signing existed are flagged `signature_status = 'UNSIGNED'`, with `unsigned_since` set to when the migration ran.

## Create the key (once per environment)

```bash
vault secrets enable transit
vault write -f transit/keys/factstore-events type=ed25519
```

Grant Factstore's Vault identity only these two capabilities:

```hcl
path "transit/sign/factstore-events" {
  capabilities = ["update"]
}
path "transit/keys/factstore-events" {
  capabilities = ["read"]
}
```

Do not grant `transit/export`, and do not make the key `exportable`. Docker Compose does this setup for you, through the `vault-init` service, against its development Vault.

## Rotate the key

```bash
vault write -f transit/keys/factstore-events/rotate
```

Vault always signs with the newest version, so new records move to `factstore-events:v<n+1>` straight away. Existing records are not re-signed. Every version stays published at `GET /api/v1/queries/event-signing-keys`, with `validFrom` and `validTo`, so older records keep verifying. Rotate on a schedule (for example yearly) and immediately if compromise is suspected.

## Disable signing

Remove Factstore's `transit/sign` capability, or disable the key in Vault. **Appends then fail closed:** a command that would write an event returns `503`, nothing is written, and `factstore_integrity_failures_total{source="signing"}` increases, which pages through the existing alert. Factstore never writes an unsigned record. Restore the capability to resume.

Never delete a key version, and never lower `min_decryption_version` past a version that signed records. The public key is the only way to verify those records.

## If a key version is compromised

1. Rotate, so new records use a new version.
2. Record the compromised version and the time of suspected compromise in the incident.
3. Verify the event log (below). Treat records signed by the compromised version after that time as unverified, and cross-check them against the source signatures stored with each fact (#187).

## Verify the event log

```http
POST /api/v1/admin/events/verify
{"from": 1, "to": 100000}
```

This needs `admin`. For each signed record it checks that the record links to the one before it (`prev_hash`), that its stored columns still hash to `record_hash`, and that its signature verifies under the published key. When the range reaches the newest record, it also checks the chain head, so a deleted newest record is caught. The response gives `checked`, `unsigned`, `valid`, and `firstFailure` with a `sequenceNumber` and a `reason`. A failure also increments `factstore_integrity_failures_total{source="signing"}`.

## Latency

`./gradlew eventSigningBenchmark` measures the added append latency and writes `build/reports/event-signing-benchmark.json`. It uses the in-memory signer, so add the round trip to your Vault. Agree the budget before release.
