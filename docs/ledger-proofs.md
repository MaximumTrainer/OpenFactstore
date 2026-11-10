# Ledger proofs

An auditor can check that a fact is in Factstore's ledger, unchanged, without trusting Factstore itself (#188).

## How it works

1. **Every event record is signed and chained** (#184). Each `domain_events` row has a `record_hash` over its canonical bytes and an Ed25519 signature from a key held in Vault, and it links to the row before it.
2. **Records are checkpointed into Merkle trees.** Every minute (`factstore.ledger.checkpoint-interval-ms`), or on demand, Factstore builds an [RFC 9162](https://www.rfc-editor.org/rfc/rfc9162) Merkle tree over the `record_hash` of every signed record since the previous checkpoint, up to `factstore.ledger.checkpoint-max-records` (default 4096).
3. **Each checkpoint root is signed and published.** A canonical statement `{"checkpoint_index", "first_sequence", "last_sequence", "leaf_count", "root_hash", "version"}` is signed with the event-signing key, stored, and published once committed. Publishing writes one JSON line to the `ledger.checkpoints` log and, if `factstore.ledger.checkpoint-file` is set, appends it to that file. Keep that copy somewhere Factstore can't rewrite: a SIEM, a write-once volume, or a repository.
4. **A proof ties a fact to a published root.** The proof contains the record's bytes and signature, the record's inclusion path, and the signed checkpoint.

## Endpoints

| Request | Needs | Returns |
|---|---|---|
| `GET /api/v1/queries/ledger/proof/{fact_id}` | `trails:read` | the proof, or `404` with `error: proof_pending` until a checkpoint covers the fact |
| `GET /api/v1/queries/ledger/checkpoints` | any authenticated caller | every signed checkpoint, oldest first |
| `GET /api/v1/queries/event-signing-keys` | any authenticated caller | the public keys records and checkpoints are signed with |
| `POST /api/v1/admin/ledger/checkpoints` | `admin` | checkpoint now: `201`, or `204` when nothing is new |

A proof looks like this:

```json
{
  "fact_id": "…",
  "ledger_index": 12345,
  "record": { "canonical": "<base64>", "record_hash": "…", "signature": "…", "signing_key_id": "factstore-events:v2", "signature_algorithm": "Ed25519" },
  "merkle_proof": { "root_hash": "…", "leaf_index": 17, "tree_size": 240, "siblings": ["…", "…"] },
  "checkpoint": { "checkpoint_index": 52, "first_sequence": 12201, "last_sequence": 12440, "leaf_count": 240, "root_hash": "…", "statement": "…", "signature": "…", "signing_key_id": "factstore-events:v2" },
  "raw_signature_verified": true
}
```

`raw_signature_verified` is the server's own check. An auditor shouldn't rely on it; that's what the verifier is for.

## Verifying independently

`tools/ledger-verifier/VerifyLedgerProof.java` is a single file that uses only the JDK and shares no code with Factstore:

```bash
curl -H "Authorization: Bearer $TOKEN" "$FACTSTORE/api/v1/queries/ledger/proof/$FACT_ID" > proof.json
curl -H "Authorization: Bearer $TOKEN" "$FACTSTORE/api/v1/queries/event-signing-keys" > keys.json
java tools/ledger-verifier/VerifyLedgerProof.java proof.json keys.json <root_hash from your published copy>
```

The verifier checks that:

- the record's bytes hash to `record_hash` and name this fact and ledger index;
- the record's signature verifies;
- the record is included under the root;
- the checkpoint statement names that root and tree size, and its signature verifies;
- the root equals the one you hold from the published copy.

It prints `VERIFIED` and exits `0`, or prints `FAILED: <reason>` and exits `1`.

Two things keep the verification independent of the server. First, get the public keys out of band: from Vault (`transit/keys/<key>`) or from a copy you made earlier. Second, pass the root from the published checkpoint log rather than the one in the proof. Factstore could then only fake a proof by changing a root it has already published.

## Limits

- Facts written before #184 aren't signed, so they have no proof. The proof endpoint answers `404` for them.
- Once #200 (cryptographic shredding) lands, shredding must keep `record_hash` valid. The tree commits to the stored bytes, so the bytes that are hashed must be ciphertext or a salted commitment, not plaintext.
- The older `/api/v1/ledger` endpoints (`LocalHashChainLedger`, `AwsQldbLedger`) are separate and unchanged. This proof path does not depend on them.
