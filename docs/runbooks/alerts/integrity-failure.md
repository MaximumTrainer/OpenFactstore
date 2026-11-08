# FactstoreIntegrityFailure

**Severity:** page

## What fired

Factstore detected that stored data does not match what it should be. The `source` label says where:

| source | Meaning |
|---|---|
| `api` | A request hit an `IntegrityException`, for example a hash or chain mismatch found while serving it. |
| `ledger` | Ledger chain verification (`POST /api/v1/ledger/verify-chain`) found a broken link. |
| `projection` | The query side could not apply a domain event, so the read model no longer matches the event log. |

This is the most serious signal Factstore produces. Until shown otherwise, treat it as possible tampering with compliance evidence.

## First checks

1. **Do not modify or "fix" the affected rows.** They are evidence.
2. For `api`: find the correlation id in the `INTEGRITY_FAILURE` notification (if routed) or the error response, and read its log lines. They name the record and the check that failed; the response body deliberately does not.
3. For `ledger`: re-run chain verification over the reported range and note `brokenAt`. Compare the database with your backups.
4. For `projection`: see [projection-failure.md](projection-failure.md). A malformed or unknown event is far more often version skew between command and query than tampering, but confirm it.
5. Check the audit log and the database access logs for writes to the affected tables from outside the application.

## Recovery

- Follow your incident process for suspected tampering: preserve evidence, restrict database access, notify security.
- Once the cause is understood: restore from a verified backup, or replay the read model from the event store for a projection fault.
- Record the incident and its resolution; auditors will ask for it.
