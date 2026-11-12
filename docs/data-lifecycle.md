# Data lifecycle: cryptographic shredding

Facts are immutable: the event log is append-only and signed (#184, #186), and auditors verify it with Merkle proofs (#188). Retention rules and GDPR still require that data can eventually be destroyed. Factstore reconciles the two with **cryptographic shredding** (#200): fact content is stored encrypted, and destroying its key destroys the content, without touching a single ledger record.

## How it works

- **One data key per tenant and month.** The tenant is the caller's organisation, or `default` for a caller bound to none. The month is the UTC `YYYY-MM` the fact was ingested. Data keys are AES-256, created on first use, and stored only wrapped by a key-encryption key in Vault Transit (`factstore.data-keys.provider=vault-transit`, key `factstore-data-keys`). The `local` provider, an in-memory key, is for development and refused under the `prod` profile.
- **What is encrypted:** a fact's `tool` payload and the signed request body (#187). Each is AES-256-GCM with the fact id as additional authenticated data, so ciphertext can't be moved between facts. Both are stored as `enc:v1:<tenant>:<period>:<base64>` in the `facts` table and in the `FactIngested` ledger record.
- **The ledger covers ciphertext.** Record hashes, signatures, chain links and Merkle proofs are all computed over the stored bytes, which are ciphertext. Destroying a key changes no stored byte, so everything still verifies.
- **What stays readable:** the envelope (fact id, schema, subject digest, repository) and the verified identity (#191). These are what an audit trail needs to show *that* a fact existed and who sent it. If identity must be erased too, that needs a policy decision and a follow-up.

## Shredding a key

Shredding is irreversible and needs two administrators:

1. One administrator asks:
   ```http
   POST /api/v1/commands/data-keys/{tenant}/{period}/shred-requests
   ```
   This returns `202` with a `request_id` and `PENDING_APPROVAL`. It returns `404` if there is no such key and `409` if the key is already shredded. The request is audited as `DATA_KEY_SHRED_REQUESTED`.
2. A **different** administrator approves:
   ```http
   POST /api/v1/commands/data-keys/shred-requests/{request_id}/approve
   ```
   The requester can't approve their own request (`403`). On approval the wrapped key is deleted. A `DataKeyShredded` record, the **destruction certificate**, is appended to the signed ledger, and the approval is audited as `DATA_KEY_SHREDDED`. The response gives `certificate_hash` and `ledger_index`.

`GET /api/v1/queries/data-keys` (admin) lists keys and their status.

## After shredding

- `GET /api/v1/queries/facts/{fact_id}` returns `410 Gone` with `{"error": "shredded", "tenant", "period"}` on every instance. Each read checks the key's status, so an instance's cache can't outlive the key.
- The period is closed: a new fact that would be written under a shredded key is refused rather than given a new key.
- `POST /api/v1/admin/events/verify` and the ledger proof for a shredded fact (`GET /api/v1/queries/ledger/proof/{fact_id}`) still verify.

## Backups

Shredding deletes the wrapped key from the database, but **database backups made before the shred still hold it**. Content in those backups stays recoverable for as long as the backups and the Vault key-encryption key both exist. Either keep backup retention within your erasure deadline, or rotate the Vault key and remove versions older than the oldest backup you keep (`vault write transit/keys/factstore-data-keys/config min_decryption_version=<n>`) once a shredded period has aged out of backups.
