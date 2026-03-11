# 10 — Environment Diff & Drift Detection

> **Status:** ✅ Existing — [GitHub Issue #9](https://github.com/MaximumTrainer/Factstore/issues/9)

## Summary

Implement the ability to compare two environment snapshots (or a snapshot against an approved baseline) to detect unauthorized or unexpected changes (drift).

## Relevance to Financial Services Requirements

Drift detection is critical for financial services compliance. Unauthorized changes in production can indicate security incidents, configuration mistakes, or circumvented controls — all of which are serious regulatory violations.

## Key Deliverables

- Snapshot diff algorithm
- Baseline management API
- Drift detection and reporting
- Drift policies (WARN / BLOCK / IGNORE)
- Frontend diff view and drift badges

## Acceptance Criteria

See [GitHub Issue #9](https://github.com/MaximumTrainer/Factstore/issues/9) for full acceptance criteria.
