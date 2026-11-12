# ADR 0002: Active-active reads, a single writer region, and the availability objectives

- **Status:** accepted
- **Date:** 2026-11-12
- **Requirement:** #192 (Active-Active Multi-Region), building on #88, #184, #186 and #188

## Context

#192 asks for active-active multi-region deployment, with eventual consistency for reads and at least 99.99% availability. That is about 4.3 minutes of downtime a month, or 52 minutes a year.

Factstore already separates writes from reads (CQRS). The command service appends to the event log, and query services project it into their own read databases (`factstore.cqrs.role`). The `factstore.region` setting and the `X-Factstore-Region` header (#88) identify the region that served a request.

Since #184, #186 and #188, the event log is one append-only chain. Each record is signed and links to the record before it, and Merkle checkpoints over it are signed and published. That shapes what "active-active writes" can mean.

## Options for writes

| Option | What it gives | Cost |
|---|---|---|
| **Single writer region, fast failover** | One chain, one sequence. Proofs (#188) stay simple and globally ordered | Writes are unavailable for the length of a failover |
| **Per-region chains, merged** | Writes accepted in every region | Every region signs its own chain. Checkpoints and proofs become per-region, and "the latest record for a commit" needs a deterministic cross-region merge rule. A much larger change to #184 and #188 |
| **Global consensus (a multi-region database or a sequencer)** | One chain with writes in every region | Each write pays a cross-region round trip. The sequencer is still a single point that has to fail over |

## Decision

- **Reads are active-active.** Every region runs query services against its own read replica and answers read and gate requests locally, whether or not the writer region is up. Reads are eventually consistent. How far each region trails is measured (`factstore_projection_delay_seconds`, `factstore_projection_last_applied_seconds`) and alerted on: more than a minute behind for ten minutes warns, and receiving nothing while writes succeed pages.
- **Writes go to a single writer region**, with a documented, tested failover to a standby region ([runbook](../runbooks/region-failover.md)). This is said plainly: writes are not active-active. The per-region-chain design is the route to active-active writes if they are ever needed. It is recorded here and not built.
- **Objectives and error budgets:**

  | SLI | Objective | Budget per 30 days |
  |---|---|---|
  | Read availability: share of `GET`/`HEAD` API requests that do not fail with 5xx, all regions | **99.99%** | 4.3 minutes |
  | Write availability: share of `POST`/`PUT`/`PATCH`/`DELETE` API requests that do not fail with 5xx | **99.9%** | 43 minutes, enough for a region failover |
  | Read freshness: projection delay | under 60 s | |

  The burn-rate alerts are in `monitoring/alerts/factstore.yml`, group `factstore-slo`. They page when the budget would be exhausted in about two days (14.4× burn over 1 h and 5 m) and warn when it would be exhausted in about five (6× over 6 h and 30 m). `promtool test rules` checks them in CI.
- Every metric carries a `region` tag (from `factstore.region`), so each region's SLIs and lag can be seen separately.

## Consequences

- 99.99% applies to reads, which is what deployment gates depend on. A pipeline can always ask "may this artifact ship?". Writes target 99.9%, because a failover has to fit in the budget.
- Signing keys (#184) are in Vault. The standby region must reach the same Transit key, or a replica of it, before it can accept writes. The failover runbook includes that step.
- Published checkpoint roots (#188) and the append-only triggers (#186) are unaffected: there is still one chain, written by one region at a time.
- Not built here: automated failover orchestration (it depends on the hosting platform), per-region chains, and region-aware client routing beyond DNS or a global load balancer.
