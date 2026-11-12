# ADR 0001: The immutable store is an append-only PostgreSQL event log

- **Status:** accepted
- **Date:** 2026-11-10
- **Requirement:** #186 (Immutable Storage Engine), building on #184 and #188

## Context

#186 requires WORM (write once, read many) semantics for stored facts, "at the storage level, not only in application code". The specification offers three options: a managed immutable ledger (AWS QLDB), a verifiable log server (Trillian), or an append-only event-sourcing model.

Factstore already writes every state change to `domain_events`, an append-only event log (#104), and only application code kept that log append-only. Two further pieces have since been added:

- each record is signed by a key held in Vault and chained to the one before it (#184);
- records are checkpointed into signed RFC 9162 Merkle trees whose roots are published outside the application, and an auditor can check a fact's inclusion with a standalone verifier (#188).

## Options

| Option | For | Against |
|---|---|---|
| **AWS QLDB** | managed, built-in digest verification | AWS ended QLDB support in July 2025; ties Factstore to AWS; a second datastore |
| **Trillian** | a purpose-built verifiable log, used by Certificate Transparency | another service and database to run, secure and back up; Factstore would still need Postgres for everything else |
| **Append-only PostgreSQL** | one datastore; the tamper evidence (#184, #188) is already independent of the engine | append-only must be enforced in the database, which is what this decision adds |

## Decision

The immutable store is the `domain_events` table in PostgreSQL, made append-only by the database:

- A `BEFORE UPDATE OR DELETE` row trigger refuses every update and delete (migration V66), whoever issues them, including the table owner. The one permitted write is **sealing** (#184): in the transaction that inserted a row, its signature columns are set once, from unsigned to signed. The trigger checks that the row was inserted by the current transaction (`xmin = pg_current_xact_id()`), so no other session can ever see an unsealed row. It also checks that no content column changes.
- A `BEFORE TRUNCATE` statement trigger refuses truncation.
- `ledger_checkpoints` gets the same protection, with no exceptions.
- The fact ingest response returns a **receipt**: the appended record's `record_hash` and `ledger_index`. The proof endpoint (#188) later shows the same hash under a signed, published root.

Tamper evidence does not rely on the triggers. Someone who controls the database server can disable a trigger, but they cannot produce a valid Vault signature or change a Merkle root that has already been published. The triggers prevent accidents and casual tampering; the signatures and published roots detect deliberate tampering.

## Consequences

- Mutable state is kept out of `domain_events`. Publication state is already in `event_outbox` (#185), and the chain head is in `event_chain_head`.
- Cryptographic shredding (#200) cannot delete or rewrite events. It has to remove the keys for data stored encrypted, which leaves the record hashes, and therefore the proofs, valid.
- Operators should also run migrations as a schema-owner role and the application as a role without `TRIGGER` privilege or ownership, so the application cannot drop the triggers. This belongs in the deployment guidance; the triggers do not depend on it.
- `AwsQldbLedger` is superseded. It should be retired in a follow-up; nothing on the fact path uses it.
- The triggers are PostgreSQL-specific. The test suite builds its schema with Hibernate on H2, so the storage-level guarantees are tested by `AppendOnlyStorageTest` in `./gradlew migrationTest`, against real PostgreSQL, which CI runs on every pull request.
