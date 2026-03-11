# 14 — Grafana Dashboard Integration

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Provide a Grafana dashboard integration for real-time visualization of compliance status, deployment gates, audit events, and security scan results. Expose Prometheus-compatible metrics and provide pre-built Grafana dashboard templates.

## Motivation

Financial services compliance teams need real-time visibility into the compliance posture of their software delivery pipeline. From the financial services scenario:

> *"Grafana Dashboards: Visualize compliance status in real-time."*
> *"Grafana pulls compliance data from PostgreSQL."*

Grafana provides:
- **Real-time dashboards** for compliance status monitoring
- **Alerting** for compliance violations and anomalies
- **Historical trend analysis** for audit preparation
- **Executive reporting** with visual compliance scorecards

## Requirements

### Prometheus Metrics Endpoint

Expose a `/actuator/prometheus` metrics endpoint with compliance-specific metrics:

| Metric | Type | Description |
|---|---|---|
| `factstore_trails_total` | Counter | Total trails created |
| `factstore_trails_compliant` | Gauge | Currently compliant trails |
| `factstore_trails_non_compliant` | Gauge | Currently non-compliant trails |
| `factstore_attestations_total` | Counter | Total attestations recorded |
| `factstore_attestations_passed` | Counter | Attestations with PASSED status |
| `factstore_attestations_failed` | Counter | Attestations with FAILED status |
| `factstore_gate_evaluations_total` | Counter | Total gate evaluations |
| `factstore_gate_blocked_total` | Counter | Deployments blocked by gate |
| `factstore_gate_allowed_total` | Counter | Deployments allowed by gate |
| `factstore_approvals_pending` | Gauge | Pending approvals |
| `factstore_drift_detected` | Gauge | Environments with active drift |
| `factstore_security_scans_passed` | Counter | Security scans passed |
| `factstore_security_scans_failed` | Counter | Security scans failed |
| `factstore_compliance_rate` | Gauge | Overall compliance percentage |

### Grafana Dashboard Templates

Provide pre-built dashboard JSON files for import:

#### 1. Compliance Overview Dashboard

- Compliance rate gauge (percentage)
- Trail status breakdown (pie chart)
- Compliance trend over time (line chart)
- Recent non-compliant trails (table)

#### 2. Security Scan Dashboard

- Security scan pass/fail rate
- Vulnerability counts by severity (bar chart)
- Critical vulnerability trend over time
- Recent failed security scans (table)

#### 3. Deployment Gates Dashboard

- Gate evaluation outcomes (allowed vs blocked)
- Block reasons breakdown (bar chart)
- Deployment frequency over time
- Recent blocked deployments (table)

#### 4. Audit & Forensics Dashboard

- Audit event volume over time
- Event type breakdown
- Actor activity (who did what)
- Environment change timeline

### Grafana Data Source Configuration

Support two data source options:

1. **Prometheus** — for real-time metrics (preferred)
2. **PostgreSQL** — for direct SQL queries against the fact store database

### Docker Compose Integration

Add Grafana and Prometheus to Docker Compose:

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - ./monitoring/dashboards:/var/lib/grafana/dashboards
      - ./monitoring/provisioning:/etc/grafana/provisioning
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
```

### API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/actuator/prometheus` | Prometheus metrics endpoint |
| GET | `/api/v1/metrics/compliance` | JSON compliance metrics summary |
| GET | `/api/v1/metrics/security` | JSON security metrics summary |

### Frontend

- Link to Grafana dashboards from the fact store web UI
- Embedded compliance gauge on the Dashboard view (using existing metrics API)

## Acceptance Criteria

- [ ] Spring Boot Actuator Prometheus endpoint enabled
- [ ] Custom compliance metrics registered and exposed
- [ ] Grafana dashboard JSON templates created (4 dashboards)
- [ ] Prometheus configuration file created
- [ ] Docker Compose updated with Prometheus and Grafana services
- [ ] Grafana provisioning configuration for auto-loading dashboards
- [ ] JSON metrics summary endpoints implemented
- [ ] Unit tests for metrics registration
- [ ] README updated with monitoring setup instructions
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- `io.micrometer:micrometer-registry-prometheus` (Spring Boot Actuator Prometheus)
- Grafana OSS Docker image: `grafana/grafana:latest`
- Prometheus Docker image: `prom/prometheus:latest`
