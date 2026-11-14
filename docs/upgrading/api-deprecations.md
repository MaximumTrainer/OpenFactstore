# Upgrading: `/api/v1` endpoints with a `/api/v2` twin are deprecated

Twenty-two `/api/v1` endpoints do the same job as a `/api/v2` endpoint with the same method and path
(#250). The `/api/v2` one is kept: it is the command/query API, which records every write in the
event log and feeds the query service. The `/api/v1` twins are deprecated from **2026-11-14** and may
be removed after **2027-05-14**. Until then they keep working unchanged.

The full list, with the decision on every duplicate, is in the [endpoint inventory](../api-inventory.md#duplicates-and-decisions).

## How to tell

Every response from a deprecated endpoint carries:

```http
Deprecation: @1794614400
Sunset: Fri, 14 May 2027 00:00:00 GMT
Link: </api/v2/trails/3f2b…/audit>; rel="successor-version"
```

The OpenAPI document (`/api-docs`, `docs/openapi.json`) marks them `deprecated: true`, so generated
clients warn at compile time. To find callers before the sunset, count requests per route in
Prometheus, for example:

```text
sum by (method, uri) (increase(http_server_requests_seconds_count{uri=~"/api/v1/(flows|trails|artifacts).*"}[7d]))
```

## How to move

In most cases, change `/api/v1/` to `/api/v2/` in the URL. The request bodies take the same fields.
Four things differ:

| What | `/api/v1` | `/api/v2` |
|---|---|---|
| Create and update responses (`POST /flows`, `PUT /flows/{id}`, `POST /trails`, `POST …/artifacts`, `POST …/attestations`, `POST …/evidence`) | The created or updated record | `{"id": …, "status": "created", "timestamp": …}`. Read the record back with the matching `GET` if you need more than its id |
| `GET /flows/{flowId}/trails` and `GET /trails/{trailId}/attestations` | A page object: `{"items": […], "page", "size", "totalItems", "totalPages"}` | A JSON array. Pass `page` and `size` to get one page, with the full count in the `X-Total-Count` header |
| Evidence upload response | The stored file's hash, name and size | Only the id; the hash, name and size are on the attestation in `GET /api/v2/trails/{trailId}/attestations` |
| Archived flows and trails | Hidden from lists unless `includeArchived=true` | The same |

So a script that read the attestation list with `jq '.items[]'` reads it with `jq '.[]'` on `/api/v2`.

The `factstore` CLI and the web app already call `/api/v2`; upgrade them and there is nothing else to do.

## Rollback

There is nothing to roll back: the deprecated endpoints answer exactly as before. Only the extra
response headers and the `deprecated` flag in the OpenAPI document are new.
