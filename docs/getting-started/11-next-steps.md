# Part 11: Next Steps

You have completed the Factstore Getting Started guide. Here is a summary of what is available today and what is coming next.

## What you can do today

| Capability | Guide |
|---|---|
| Define delivery processes with required evidence gates | [Flows](./04-flows.md) |
| Record pipeline runs with full git metadata | [Trails](./05-trails.md) |
| Attach SHA-256-identified build artifacts | [Artifacts](./06-artifacts.md) |
| Record evidence: tests, scans, reviews | [Attestations](./07-attestations.md) |
| Check compliance of a Trail against a Flow | [Attestations → Checking compliance](./07-attestations.md#checking-compliance) |
| Explore all endpoints interactively | Swagger UI at `/swagger-ui/index.html` |

## Roadmap

The following features are actively planned. Each links to a GitHub issue with full requirements and acceptance criteria.

### Infrastructure

| Feature | Issue | Description |
|---|---|---|
| Persistent Storage (PostgreSQL) | [#12](https://github.com/MaximumTrainer/Factstore/issues/12) | Replace H2 in-memory DB; data survives restarts |

### Security & Access Control

| Feature | Issue | Description |
|---|---|---|
| Authentication & Service Accounts | [#34](https://github.com/MaximumTrainer/Factstore/issues/34) | API key auth; service accounts for CI/CD pipelines |
| HashiCorp Vault Integration | [#21](https://github.com/MaximumTrainer/Factstore/issues/21) | Secret management integration |

### Compliance & Governance

| Feature | Issue | Description |
|---|---|---|
| Artifact Build Provenance | [#5](https://github.com/MaximumTrainer/Factstore/issues/5) | SLSA-compatible provenance; cryptographic signatures |
| Release Approval Workflow | [#6](https://github.com/MaximumTrainer/Factstore/issues/6) | Multi-party sign-off before deployment |
| Deployment Gate & Policy Engine | [#7](https://github.com/MaximumTrainer/Factstore/issues/7) | Block non-compliant artifacts at deploy time |
| Regulatory Compliance Framework | [#25](https://github.com/MaximumTrainer/Factstore/issues/25) | SOX, PCI-DSS, GDPR alignment |

### Runtime Observability

| Feature | Issue | Description |
|---|---|---|
| Environment Tracking & Snapshots | [#4](https://github.com/MaximumTrainer/Factstore/issues/4) | Record what is running where, and when |
| Environment Diff & Drift Detection | [#9](https://github.com/MaximumTrainer/Factstore/issues/9) | Detect unauthorised changes between snapshots |
| Runtime Forensics & Audit Log | [#8](https://github.com/MaximumTrainer/Factstore/issues/8) | Immutable append-only log of every change event |

### Integrations

| Feature | Issue | Description |
|---|---|---|
| CI/CD Pipeline Event Webhooks | [#10](https://github.com/MaximumTrainer/Factstore/issues/10) | Receive events from GitHub Actions, Jenkins, etc. |
| Jira & Confluence Integration | [#23](https://github.com/MaximumTrainer/Factstore/issues/23) | Link Jira tickets to Trails |
| OPA (Open Policy Agent) Integration | [#22](https://github.com/MaximumTrainer/Factstore/issues/22) | Advanced policy evaluation with OPA |
| Security Scan Integration | [#26](https://github.com/MaximumTrainer/Factstore/issues/26) | OWASP ZAP, Snyk, Trivy native parsers |

### Developer Experience

| Feature | Issue | Description |
|---|---|---|
| Linux Command Line Tool | [#33](https://github.com/MaximumTrainer/Factstore/issues/33) | Go-based CLI for all API operations |
| Factstore Website | [#31](https://github.com/MaximumTrainer/Factstore/issues/31) | Public-facing product site |
| Comprehensive User Documentation | [#30](https://github.com/MaximumTrainer/Factstore/issues/30) | Extended docs beyond this getting-started guide |

### Reporting & Observability

| Feature | Issue | Description |
|---|---|---|
| Enhanced Query & Reporting API | [#11](https://github.com/MaximumTrainer/Factstore/issues/11) | Time-travel queries, compliance reports, exports |
| Notifications & Compliance Alerts | [#13](https://github.com/MaximumTrainer/Factstore/issues/13) | Slack/webhook alerts on compliance violations |
| Grafana Dashboard & Prometheus Metrics | [#24](https://github.com/MaximumTrainer/Factstore/issues/24) | Operational metrics and dashboards |
| Immutable Ledger Support | [#28](https://github.com/MaximumTrainer/Factstore/issues/28) | Hash-chain & AWS QLDB for tamper-proof records |

## Contributing

Factstore is open for contributions. To get started:

1. Fork the repository: [github.com/MaximumTrainer/Factstore](https://github.com/MaximumTrainer/Factstore)
2. Pick an open issue — especially ones labelled [`enhancement`](https://github.com/MaximumTrainer/Factstore/labels/enhancement) or [`good first issue`](https://github.com/MaximumTrainer/Factstore/labels/good%20first%20issue)
3. Open a pull request against `main`

If you find a bug or have a feature idea that is not already listed, open a new issue.

## Getting help

- Browse all open issues: [github.com/MaximumTrainer/Factstore/issues](https://github.com/MaximumTrainer/Factstore/issues)
- Explore the API interactively: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

Previous: [← Part 10: Approvals](./10-approvals.md)
