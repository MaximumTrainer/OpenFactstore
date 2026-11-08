# Alert runbooks

One page per alert in [`monitoring/alerts/factstore.yml`](../../../monitoring/alerts/factstore.yml). Each alert's `runbook_url` annotation links here. Routing is in [`monitoring/alertmanager.yml`](../../../monitoring/alertmanager.yml): `page` goes to on-call, `warn` to the team channel.

| Alert | Severity | Page |
|---|---|---|
| `FactstoreHigh5xxRatio` | page | [high-5xx-ratio.md](high-5xx-ratio.md) |
| `FactstoreIntegrityFailure` | page | [integrity-failure.md](integrity-failure.md) |
| `FactstoreDbPoolSaturated` | warn | [db-pool-saturated.md](db-pool-saturated.md) |
| `FactstoreProjectionFailure` | warn | [projection-failure.md](projection-failure.md) |
| `FactstoreEventPublishFailing` | warn | [event-publish-failing.md](event-publish-failing.md) |
| `FactstoreAuthFailureSpike` | warn | [auth-failure-spike.md](auth-failure-spike.md) |
| `FactstoreNotificationDeadLetter` | warn | [notification-dead-letter.md](notification-dead-letter.md) |

## Finding the logs for a failure

Every request has a correlation id. A client receives it in the `X-Request-Id` response header and, on any error, in the `correlationId` field of the body. A caller may also supply its own `X-Request-Id` (letters, digits, `.`, `_`, `:`, `-`, up to 128 characters), which is then used as-is.

Outside local development the logs are JSON, one object per line, so the id finds every line for that request:

```bash
kubectl logs deploy/factstore-command | jq -c 'select(.correlationId == "<id>")'
```

```bash
docker compose logs backend-command | grep '"correlationId":"<id>"'
```

The `service` field (`command`, `query`, or `all` for an unsplit deployment) says which service wrote the line. Set `factstore.logging.format=plain` for human-readable logs; the `local` and `dev` profiles do this already.

## Routing system failures as notifications

Two notification triggers exist for operations, alongside the compliance ones: `SYSTEM_ERROR` (any unexpected 5xx) and `INTEGRITY_FAILURE`. Create a notification rule for either to send it to Slack or a webhook. The message names the route and correlation id, never the exception text.
