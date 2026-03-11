# 18 — Immutable Ledger Support

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Add support for an immutable ledger backend (such as AWS QLDB or a blockchain-based solution) to provide cryptographically verifiable, tamper-proof storage for compliance-critical facts. This complements the PostgreSQL storage by providing a secondary, immutable record of all compliance events.

## Motivation

From the financial services compliance scenario:

> **Immutable Ledger DB** — *"Stores tamper-proof facts for auditability"* (e.g., AWS QLDB, Hyperledger Fabric)

While the Immutable Audit Log (Issue #8) provides append-only behavior at the application level, regulators in financial services may require **cryptographic proof** that records have not been tampered with. An immutable ledger provides:

- **Cryptographic verification** — each record is hash-chained to its predecessor
- **Tamper detection** — any modification to historical records is detectable
- **Regulatory compliance** — meets the strictest audit trail requirements (SOX Section 802)
- **Independent verification** — third parties can verify the integrity of the audit trail

## Requirements

### Immutable Ledger Architecture

The immutable ledger operates as a **secondary write destination** — all compliance-critical facts are written to both PostgreSQL (for queries) and the immutable ledger (for tamper-proof verification).

```
Fact Store Service
    │
    ├──► PostgreSQL (primary — queryable, mutable)
    │
    └──► Immutable Ledger (secondary — tamper-proof, verifiable)
         ├── AWS QLDB adapter
         ├── Hyperledger Fabric adapter (future)
         └── Local hash-chain adapter (for development/testing)
```

### Data Model

#### LedgerEntry

| Field | Type | Description |
|---|---|---|
| `entryId` | String | Ledger-assigned unique ID |
| `factId` | UUID | Corresponding fact store fact ID |
| `eventType` | String | Type of compliance event |
| `contentHash` | String | SHA-256 hash of the fact content |
| `previousHash` | String | Hash of the previous ledger entry (chain) |
| `timestamp` | Timestamp | Ledger-recorded timestamp |
| `metadata` | JSON | Additional context |

### Outbound Port

```kotlin
interface IImmutableLedger {
    fun recordFact(fact: LedgerFact): LedgerReceipt
    fun verifyFact(factId: UUID): VerificationResult
    fun getHistory(factId: UUID): List<LedgerEntry>
    fun verifyChainIntegrity(from: Timestamp, to: Timestamp): ChainVerificationResult
}
```

### Ledger Adapters

#### 1. Local Hash-Chain Adapter (Development/Testing)

- In-memory or file-based hash chain
- SHA-256 linked entries
- No external dependencies
- Suitable for unit tests and local development

#### 2. AWS QLDB Adapter

- Uses Amazon QLDB for production deployment
- Leverages QLDB's built-in cryptographic verification
- Journal export for independent verification

#### 3. Hyperledger Fabric Adapter (Future)

- Permissioned blockchain for multi-party verification
- Smart contract for fact validation rules

### API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/ledger/entries` | List ledger entries (paginated) |
| GET | `/api/v1/ledger/entries/{factId}` | Get ledger entry for a specific fact |
| POST | `/api/v1/ledger/verify/{factId}` | Verify the integrity of a specific fact |
| POST | `/api/v1/ledger/verify-chain` | Verify chain integrity for a date range |
| GET | `/api/v1/ledger/status` | Ledger health and sync status |

### Verification Response

```json
{
    "factId": "fact-12345",
    "verified": true,
    "contentHash": "sha256:abc123...",
    "chainPosition": 42,
    "previousHash": "sha256:def456...",
    "ledgerTimestamp": "2025-02-20T14:05:30Z",
    "verifiedAt": "2025-02-21T10:00:00Z"
}
```

### Configuration

```yaml
ledger:
  enabled: ${LEDGER_ENABLED:false}
  type: ${LEDGER_TYPE:local}  # local | qldb | hyperledger
  qldb:
    ledger-name: ${QLDB_LEDGER_NAME:factstore-ledger}
    region: ${AWS_REGION:us-east-1}
  local:
    storage-path: ${LEDGER_STORAGE_PATH:./data/ledger}
```

### Frontend

- Ledger verification status badge on fact/attestation detail pages
- Chain integrity verification page
- Ledger entry browser with hash chain visualization
- Verification certificate download (for auditors)

## Acceptance Criteria

- [ ] `IImmutableLedger` port interface defined
- [ ] Local hash-chain adapter implemented (for development/testing)
- [ ] AWS QLDB adapter implemented
- [ ] Dual-write logic (PostgreSQL + ledger) for compliance events
- [ ] Fact verification endpoint
- [ ] Chain integrity verification endpoint
- [ ] Ledger health and sync status endpoint
- [ ] Unit tests with local hash-chain adapter
- [ ] Configuration for enabling/disabling ledger
- [ ] Frontend verification status display
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- AWS QLDB: `software.amazon.qldb:amazon-qldb-driver-java`
- Local adapter: No external dependencies (SHA-256 from JDK)
- The local hash-chain adapter should be the default for development
