# Flow Templates

A **flow template** is a reusable definition of the gates a release must pass. Rather than
hand-listing every required attestation for every service, a team starts from a template and edits
it.

There are two kinds, because they answer different questions:

| Category | Question it answers | Examples |
|---|---|---|
| `SERVICE_TYPE` | *What does this organisation expect of a service of this shape?* | `service-public-api`, `service-internal`, `service-batch-job`, `service-frontend` |
| `FRAMEWORK` | *What does this regulation or standard require?* | `slsa-level-2`, `slsa-level-3`, `pci-dss-v4`, `sox-itgc`, `gdpr-art32` |

A flow commonly wants one of each: a service-type baseline plus whatever regulation applies.

---

## The service-type catalogue

These ship with the product as the starting point for an organisation's house standard.

| Template | Requires |
|---|---|
| `service-public-api` | unit-tests, sast, dependency-scan, image-scan, api-tests, integration-tests, peer-review |
| `service-internal` | unit-tests, sast, dependency-scan, image-scan, peer-review |
| `service-batch-job` | unit-tests, sast, dependency-scan, image-scan |
| `service-frontend` | unit-tests, sast, dependency-scan, licence-scan, accessibility-check |

The shape of the differences is deliberate:

- **`service-public-api`** has the widest blast radius, so it carries contract, integration and API
  testing on top of the internal baseline.
- **`service-batch-job`** has no inbound API surface, so the emphasis is entirely on what ships.
- **`service-frontend`** usually has no container image to scan, but third-party licence exposure
  and accessibility matter in a way they do not for a backend service.

These are a **starting point, not a mandate**. Edit them for your organisation — see
*Publishing your own templates* below.

---

## Creating a flow from a template

In the web UI, the Create Flow dialog offers a template picker: pick a service type, optionally add
a regulatory framework, see exactly which attestations the result will require, and edit the list
before saving. **Blank flow** remains available.

Over the API:

```http
POST /api/v2/flows
{
  "name": "payments-api",
  "description": "Payments service gates",
  "requiredAttestationTypes": [],
  "templateIds": ["service-public-api", "pci-dss-v4"]
}
```

`templateId` (singular) is accepted for the single-template case. An explicit `templateYaml` always
wins over a template id, so a hand-written template is never overwritten.

### Snapshot, not a live link

A template's YAML is **copied onto the flow at creation**. It is not a live reference.

This is deliberate: if templates were linked live, updating a template would change what an
in-flight release is judged against, retrospectively and invisibly. Instead the flow keeps the
definition it was created with, and drift is reported so a team can choose when to re-apply.

---

## Combining templates

```http
POST /api/v1/hub/templates/compose
{ "templateIds": ["service-public-api", "pci-dss-v4"] }
```

```json
{
  "templateIds": ["service-public-api", "pci-dss-v4"],
  "templateYaml": "version: 1\ntrail:\n  attestations:\n    - name: unit-tests\n      type: junit\n…",
  "requiredAttestations": ["unit-tests", "sast", "dependency-scan", "…"],
  "conflicts": []
}
```

The merge is a **union keyed on attestation name**: a gate required by both templates is required
once. Where the same name is required with two *different* types the templates genuinely disagree —
the first template wins so the result is still usable, and the disagreement is reported in
`conflicts` rather than buried. The UI shows conflicts on the picker.

Templates are applied in the order given, so put the more specific template first if you want its
types to win.

---

## Drift

```http
GET /api/v1/flows/{id}/template-drift
```

```json
{
  "flowId": "uuid",
  "templateId": "service-public-api",
  "templateVersion": "1.0",
  "currentTemplateVersion": "1.1",
  "drifted": true,
  "missingFromFlow": ["integration-tests"],
  "addedToFlow": ["chaos-test"]
}
```

- `missingFromFlow` — the template requires it, this flow no longer does. Usually means a team has
  removed a gate; sometimes means the template has since added one.
- `addedToFlow` — this flow requires it, the template does not. A local addition, which may be
  worth promoting into the template.

A flow not created from a template reports `drifted: false` rather than failing. The flow detail
page shows a banner when a flow has drifted.

Drift is **reported, never corrected automatically** — re-applying a template changes how existing
trails evaluate on their next assertion, which is a decision for the team that owns the service.

---

## Publishing your own templates

An organisation can publish its own templates alongside the built-ins, so a platform team defines
the house standard rather than only consuming ours.

```http
POST /api/v1/hub/templates/custom
{
  "templateId": "service-public-api",
  "name": "Acme Public API",
  "description": "Acme's baseline for anything internet-facing",
  "category": "SERVICE_TYPE",
  "serviceType": "public-api",
  "orgSlug": "acme",
  "templateYaml": "version: 1\ntrail:\n  attestations:\n    - name: unit-tests\n      type: junit\n"
}
```

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/hub/templates` | Built-ins and org templates. Filter with `category` and `orgSlug` |
| `GET` | `/api/v1/hub/templates/{id}` | One template |
| `POST` | `/api/v1/hub/templates/compose` | Combine several templates |
| `GET` | `/api/v1/hub/templates/custom` | Org templates only |
| `POST` | `/api/v1/hub/templates/custom` | Publish an org template |
| `PUT` | `/api/v1/hub/templates/custom/{id}` | Update an org template |
| `DELETE` | `/api/v1/hub/templates/custom/{id}` | Withdraw an org template |

**An org template whose `templateId` matches a built-in shadows it.** That is how a platform team
overrides the house standard — publish `service-public-api` for your org and every flow created
from that id gets your version, without forking the product.

A template that cannot be parsed is rejected at publication: a template nobody can read is worse
than no template.

---

## Extending the built-in catalogue

Built-in templates are YAML files on the classpath at `hub-templates/*.yml`. To add one to the
product, drop a file in `backend/src/main/resources/hub-templates/`:

```yaml
id: service-mobile-app
name: Mobile Application
description: >-
  Baseline gates for a shipped mobile application.
category: service-type        # or: framework
serviceType: mobile-app       # service-type templates only
framework: house-standard
version: "1.0"
trail:
  attestations:
    - name: unit-tests
      type: junit
    - name: dependency-scan
      type: snyk
    - name: store-submission-check
      type: generic
```

| Field | Notes |
|---|---|
| `id` | Stable; a flow records it, and drift is computed against it |
| `category` | `service-type` or `framework`. Anything else is treated as `framework` |
| `serviceType` | The shape of service the template is a baseline for |
| `version` | Bump when the gates change; drift reports the version a flow was created from |
| `trail.attestations` | `name` is what an attestation is matched on; `type` is the tool that produced it |

Attestations may also be nested under `artifacts:` when a gate applies to a specific image rather
than the whole release, and can carry an `if:` condition — see the flow template format in
[API_REFERENCE.md](./API_REFERENCE.md).

For an organisation's own templates, prefer `POST /api/v1/hub/templates/custom` over editing the
product: it needs no deployment and can be scoped to one `orgSlug`.
