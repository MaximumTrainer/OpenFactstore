# Part 1: Overview

Factstore is a software supply chain traceability platform. It gives your engineering organisation a tamper-evident record of every artefact you build, every quality gate you pass, and every deployment you make — so you can answer compliance questions with evidence rather than guesswork.

## How this guide is structured

This guide walks you through every concept in Factstore from the ground up, in the same order you would set things up for a real delivery pipeline:

| Part | Topic | Status |
|------|-------|--------|
| [Part 2: Setup & Access](./02-setup.md) | Running Factstore and calling the API | ✅ Available |
| [Part 3: Authentication](./03-authentication.md) | Securing API access with keys | 🚧 Coming soon |
| [Part 4: Flows](./04-flows.md) | Defining your delivery process | ✅ Available |
| [Part 5: Trails](./05-trails.md) | Recording a single execution of a process | ✅ Available |
| [Part 6: Artifacts](./06-artifacts.md) | Attaching binary provenance to built artefacts | ✅ Available |
| [Part 7: Attestations](./07-attestations.md) | Recording evidence (tests, scans, reviews) | ✅ Available |
| [Part 8: Environments](./08-environments.md) | Tracking what is running where | 🚧 Coming soon |
| [Part 9: Policies](./09-policies.md) | Enforcing compliance at deploy time | 🚧 Coming soon |
| [Part 10: Approvals](./10-approvals.md) | Gating releases with human sign-off | 🚧 Coming soon |
| [Part 11: Next Steps](./11-next-steps.md) | Roadmap, CLI, and contributing | — |

You can read this guide linearly or jump to the section you need.

## Core concepts

### Flow

A **Flow** represents a repeatable software delivery process — for example, the CI/CD pipeline that builds and ships your backend service. A Flow defines the compliance requirements that every execution of that process must satisfy (e.g. "unit tests must pass", "a security scan must be attached").

### Trail

A **Trail** is a single execution of a Flow. Each time your CI pipeline runs — triggered by a commit, a pull request, or a ticket — you create a Trail to record everything that happened during that run.

### Artifact

An **Artifact** is a versioned, immutable build output: a Docker image, a JAR file, a binary. Factstore identifies artifacts by their **SHA-256 digest**, so the record is tied to the exact bytes — not just a name or tag.

### Attestation

An **Attestation** is a piece of evidence that a quality gate was completed. Examples include: JUnit test results, a Snyk security scan report, a pull-request review, or a Jira ticket link. Attestations are append-only and immutable once recorded.

### Compliance assertion

At any point you can ask Factstore: *"Is this artifact compliant with this Flow's requirements?"* The `POST /api/v1/assert` endpoint evaluates the attestations on a Trail against the Flow's required attestation types and returns a compliance verdict.

---

Next: [Part 2: Setup & Access →](./02-setup.md)
