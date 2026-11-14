# Dogfooding OpenFactstore

OpenFactstore records the compliance facts for its own build and gates its own release on them.
The workflow is [`.github/workflows/dogfood.yml`](../.github/workflows/dogfood.yml).

This is worth doing for two reasons beyond credibility: it is an end-to-end test of the product
against a real server rather than a test harness, and it is the fastest way to find out which parts
of the API are awkward to drive from a pipeline.

---

## How it works today

The workflow builds OpenFactstore **from the commit under test**, runs it against a Postgres service
container, and judges that commit with that instance.

```
build the JAR  →  start it against Postgres  →  create a flow
      →  open a trail keyed on <repo>@<run_id>
      →  run each real gate, attesting PASSED or FAILED
      →  register the built JAR as the artifact
      →  assert the trail  →  pass or fail the workflow
```

The instance is transient and torn down with the job. That is a deliberate choice: dogfooding this
way needs **no hosted deployment, no secrets, and no network egress**, so it runs on a fork and on
a pull request from a fork, where secrets are unavailable. The cost is that the evidence does not
outlive the run — see *Running against a persistent instance* below for when that matters.

The workflow does seed a fixed admin API key into that instance
(`security.bootstrap.api-key`). Authorisation rules are always live, so creating a flow needs a
credential even with enforcement off. The key is in the workflow in plain sight on purpose: it
grants nothing outside the container that is destroyed with the job.

### The gates it attests

Each gate runs with `continue-on-error: true` and records its own outcome, so a failing gate
produces a `FAILED` attestation instead of aborting the run before the evidence is written. The
**assert step is what fails the workflow** — exactly as it would in a customer's pipeline.

| Attestation | What actually runs |
|---|---|
| `backend-tests` | `./gradlew test` |
| `contract-tests` | `./gradlew contractTest` (Pact provider verification) |
| `frontend-tests` | `npm run test:unit` |
| `cli-tests` | `go test ./...` |
| `build` | `npm run build` (`vue-tsc` + production build) |

A step that never ran is attested `PENDING`, not `PASSED`, so the assert reports it as missing
rather than as a pass.

### Why not the `service-public-api` template?

The obvious move is to judge OpenFactstore's own release against the service-type baseline it
ships. We deliberately do not, yet.

`service-public-api` requires `sast`, `dependency-scan`, `image-scan`, `api-tests`,
`integration-tests` and `peer-review`.

| Template gate | State in this repository |
|---|---|
| `sast` | **Runs** — CodeQL, via GitHub's *default setup* (actions, go, javascript-typescript). But there is no workflow file to take a step outcome from: the result lives behind the code-scanning API. |
| `dependency-scan` | Not run as a gate. |
| `image-scan` | Not run as a gate. |
| `api-tests` | Partially — Pact contract verification is attested as `contract-tests`. |
| `integration-tests` | Runs as a **separate workflow** (`verify-factstore.yml`), not attested to this trail. |
| `peer-review` | Enforced by branch protection, not attested. |

Asserting against the template today would leave two options, both bad: fail every run, or attest
gates that never executed. **A fact store whose facts nobody produced is worthless**, and that is
precisely the failure mode this product exists to prevent — so the flow requires exactly the five
gates the workflow really runs.

Closing the gap, in increasing order of effort:

1. **`integration-tests`** — the cheapest, because the gate already exists.
   [`verify-factstore.yml`](../.github/workflows/verify-factstore.yml) runs a persona suite against
   a live instance; have it attest against the same `externalId` (see *Cross-pipeline evidence*)
   instead of standing alone.
2. **`peer-review`** — attest the PR approval on merge, from the pull-request event.
3. **`sast`** — read the CodeQL result for the commit from the code-scanning API
   (`GET /repos/{owner}/{repo}/code-scanning/alerts?ref=...`) and attest it. Requires
   `security-events: read`, which is why it is not simply a step outcome.
4. **`dependency-scan`** — Dependabot alerts are available over the API on the same basis; or add
   an explicit `osv-scanner`/`snyk` step whose outcome can be attested directly.
5. **`image-scan`** — needs a published image to scan first; scan it (Trivy or Grype) and attest.

At that point the flow can adopt the template and the drift report will show it matching.

---

## Cross-pipeline evidence

The trail is keyed on `externalId = <repo>@<run_id>`. Creation is idempotent on
`(flowId, externalId)`, so:

- a **re-run** of the workflow joins the same trail rather than forking the evidence;
- **another workflow** in the same run can attest to it without being passed a UUID.

That is how `verify-factstore.yml` would contribute its `integration-tests` attestation:

```yaml
- name: Attest integration tests to the release trail
  if: always()
  env:
    OUTCOME: ${{ steps.personas.outcome }}
  run: |
    TRAIL_ID=$(curl -sf "${FACTSTORE_URL}/api/v1/trails/lookup?flowId=${FLOW_ID}&externalId=${RELEASE_ID}" \
      -H "Authorization: Bearer ${{ secrets.FACTSTORE_API_TOKEN }}" \
      | jq -r .id)
    factstore attest generic --trail-id "$TRAIL_ID" \
      --type integration-tests --name integration-tests \
      --status "$([ "$OUTCOME" = success ] && echo PASSED || echo FAILED)"
```

The general pattern, with a worked GitHub Actions example, is in
[ci-integration.md](./ci-integration.md#evidence-from-several-pipelines-on-one-trail).

---

## Running against a persistent instance

A transient instance proves the mechanism but keeps no history: you cannot look back at last
month's releases, and the delivery metrics have nothing to measure. For that, point the workflow at
a long-lived instance:

```yaml
env:
  FACTSTORE_URL: ${{ vars.FACTSTORE_URL }}
```

and give the attest script a credential:

```yaml
env:
  FACTSTORE_TOKEN: ${{ secrets.FACTSTORE_TOKEN }}
```

`.github/scripts/attest.sh` already sends `Authorization: Bearer` when `FACTSTORE_TOKEN` is set, so
no other change is needed.

Two consequences to plan for:

- **Secrets are unavailable to pull requests from forks.** Either keep the transient path for PRs
  and use the persistent instance only on `main`, or accept that fork PRs skip the gate.
- **The flow should be created once, not per run.** Replace the create-flow step with a fixed
  `FLOW_ID` in repository variables, so every release is judged against the same definition and the
  trail history is comparable.

### Where to host it

Deliberately left open — it is a decision about cost, data residency and who operates it, not a
technical one, and it is the maintainer's to make. OpenFactstore is a JVM service plus Postgres, so
anything that runs a container works: Fly.io, Fargate, Cloud Run, a VM, or an existing Kubernetes
cluster. [DEPLOY.md](../DEPLOY.md) covers the deployment mechanics.

One option raised in issue #150 is worth recording because it solves a specific problem — exposing
the API to GitHub-hosted runners without giving it a public address:

- run the container anywhere with outbound network access;
- put it behind a **Cloudflare Tunnel** (`cloudflared`), so it needs no public IP or inbound
  firewall rule;
- require **Cloudflare Access service tokens** at the edge, so a request without
  `CF-Access-Client-Id` / `CF-Access-Client-Secret` is dropped before it reaches the application.

That is a sound perimeter, and it composes with the API's own authentication rather than replacing
it — the edge decides *whether a request arrives*, the application still decides *what that
credential may do* (#155). It is **not implemented here**: it requires a Cloudflare account, a
tunnel token and a hosting choice this repository does not make on an operator's behalf.

The same issue proposed replacing the persistence layer with Cloudflare R2 as an event store and D1
as the read model. That is a much larger change than a deployment topology — it would swap the JPA
adapters and the Flyway-managed schema for a different storage model, and it is not required for
dogfooding. It should be assessed on its own merits, as its own piece of work, rather than arriving
as a side effect of choosing where to run the service.

---

## Reading the results

The workflow writes a job summary with the verdict, the trail id, the artifact digest and any
missing or failed gates, and prints the trail, its attestations and its audit log at the end of the
run. Against a persistent instance, the same release appears in the UI under **Flows → the release
flow**, and contributes to **Metrics** (deployment frequency, lead time, gate block rate).
