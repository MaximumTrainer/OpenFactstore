# Streaming to a SIEM (Splunk, Datadog)

Factstore streams every authentication attempt, every policy rejection and every read or write to your SIEM in real time, as [OCSF](https://schema.ocsf.io) 1.3.0 events (#199).

## What is streamed

| When | OCSF class | Key fields |
|---|---|---|
| A credential is presented (API key, CI workload token, session) | Authentication (3002), `activity_id` 1 Logon | `status_id` 1 Success or 2 Failure, `status_detail` (for example `REVOKED`, `EXPIRED`), `user.name`, `auth_protocol` (`api_key`, `oidc_workload`, `session`), `src_endpoint.ip` |
| A person signs in, fails to sign in, or signs out | Authentication (3002); Logoff is `activity_id` 2 | `auth_protocol: interactive`, `unmapped.factstore_event_type` (`USER_SIGNED_IN`, `USER_SIGN_IN_FAILED`, `USER_SIGNED_OUT`) |
| Every API call (`/api/**`), allowed or refused | API Activity (6003) | `activity_id` 1 Create (POST), 2 Read (GET), 3 Update (PUT/PATCH), 4 Delete; `http_request.http_method`, `http_request.url.path`, `http_response.code`, `actor.user.name` (`anonymous` when no credential), `status_detail` on a refusal, `duration` in ms |
| Every audit record (facts, schemas, keys, gates, approvals, flows, trails…) | API Activity (6003), except sign-in events (see above) | `unmapped.factstore_event_type`, `unmapped.factstore_payload`. `GATE_BLOCKED`, `AUTH_DENIED` and `APPROVAL_REJECTED` are failures: policy rejections |

Every event carries `metadata.correlation_uid`, the request's `X-Request-Id`. A call's authentication event, its API activity and any audit record it wrote can therefore be joined, and joined to Factstore's other logs.

Policy rejections appear as API Activity events with `status_id` 2. Their `status_detail` is the reason, for example `Forbidden`, `cross_workload_injection`, `signature_invalid` or `schema_nonconformant`.

## How it is delivered

Factstore writes each event as one JSON line on stdout, under the `factstore.audit` logger, with the OCSF event in the `ocsf` field:

```json
{"timestamp":"…","level":"INFO","logger":"factstore.audit","message":"API Activity","correlationId":"…","service":"command","ocsf":{"class_uid":6003,"activity_id":1,"status_id":1,"http_request":{"http_method":"POST","url":{"path":"/api/v1/commands/facts"}},"…":"…"}}
```

Your platform's log collector forwards those lines. Factstore never connects to the SIEM itself, so:

- **it never waits on the SIEM.** A slow or unavailable SIEM can't slow or fail a request;
- **if the SIEM is down,** the collector buffers and retries. Size its buffer for your longest expected outage, and alert on the collector's backlog and dropped-record metrics. For Fluent Bit those are `fluentbit_output_retries_failed_total` and `fluentbit_output_dropped_records_total`.

The `factstore.audit` logger is pinned at INFO, so lowering the root log level can't silence it. JSON logging must be on (`factstore.logging.format=json`, the default outside local development).

### Splunk (HTTP Event Collector) with Fluent Bit

```ini
[INPUT]
    Name   tail
    Path   /var/log/containers/*factstore*.log
    Parser docker

[FILTER]
    Name   grep
    Match  *
    Regex  logger ^factstore\.audit$

[OUTPUT]
    Name         splunk
    Match        *
    Host         splunk.example.com
    Port         8088
    Splunk_Token ${SPLUNK_HEC_TOKEN}
    TLS          On
    event_key    $ocsf
    event_sourcetype ocsf
    Retry_Limit  False
    storage.total_limit_size 5G
```

### Datadog with the Datadog Agent

Enable container log collection and tag Factstore's logs. Then, in Datadog, create a pipeline for `logger:factstore.audit` with a JSON remapper on `ocsf`, or use the OCSF integration. The Agent buffers and retries on its own.

```yaml
# Factstore container labels
com.datadoghq.ad.logs: '[{"source": "openfactstore", "service": "factstore"}]'
```

## Not covered

- Factstore does not push to SIEM HTTP endpoints directly. Delivery is the collector's job, so that Factstore stays independent of the SIEM's availability.
- Integrity failures (#184, #188) are logged and counted (`factstore_integrity_failures_total`), but not yet emitted as OCSF Detection Findings (2004).
