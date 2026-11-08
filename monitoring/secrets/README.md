# Alertmanager receiver secrets

`monitoring/alertmanager.yml` reads each receiver's webhook URL from a file in this directory, so no address is ever committed. Everything here except this README is git-ignored.

| File | Receiver | Point it at |
|---|---|---|
| `page-webhook-url` | `page` (on-call) | Your paging provider's alert API, or a webhook bridge to it |
| `warn-webhook-url` | `warn` (team channel) | A Slack or Teams incoming webhook |
| `prometheus-scrape-key` | Prometheus, not Alertmanager | The API key Prometheus presents to `/actuator/prometheus`. It carries only `metrics:read`. `scripts/bootstrap-env.sh` writes it, along with the matching `SECURITY_BOOTSTRAP_METRICS_API_KEY` in `.env` |

Each file holds the URL on a single line. For local use with Docker Compose:

```bash
printf '%s' 'https://hooks.example.invalid/your-webhook' > monitoring/secrets/warn-webhook-url
```

```bash
docker compose --profile alerting up -d alertmanager
```

In Kubernetes, mount a Secret at `/etc/alertmanager/secrets` instead; with Vault, render the files with Vault Agent.
