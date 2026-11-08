# API authorisation matrix

Every endpoint and the rule that guards it (#155 FR-4). The table between the markers is **generated from the code and checked by `EveryEndpointIsAuthorisedTest`**, so it cannot drift. A build with an endpoint that has no rule, or a table that no longer matches, fails.

Regenerate after changing an annotation:

```bash
cd backend && ./gradlew test --tests "*EveryEndpointIsAuthorisedTest*" -Dfactstore.update-matrix=true -Pkotlin.compiler.execution.strategy=in-process
```

## How to read a rule

| Rule | Who passes |
|---|---|
| `@authz.has('<scope>')` | A user whose role grants the scope, or an API key carrying it. See the role table in [authentication.md](authentication.md#the-role-model). `admin` grants every scope. |
| `@authz.hasAny(...)` | A caller holding at least one of the listed scopes. |
| `@authz.authenticated()` | Any signed-in user or valid API key. Used for reads, and for writes that only touch the caller's own session or notifications. |
| `hasAuthority('SCOPE_<scope>')` | Older, strict rule: the scope is required even when enforcement is off. Kept on the endpoints that had it before #155's matrix work (flow, policy, key, member, service-account and SSO management). |
| `public: <reason>` | No Factstore credential. Each one says why, and anything it changes is authenticated another way (for example a webhook's HMAC signature). |

### During the rollout

`@authz` rules (`config/AuthorisationRules.kt`) follow the rollout order in #155 §5:

- **`security.enforce-auth=false`** (today's default): anonymous callers pass, as the route rules already allow. A caller that **does** present a credential is held to its scopes, so a CI key with only `attestations:write` cannot delete a trail even on a permissive instance.
- **`security.enforce-auth=true`**: the scope is required. Anonymous requests get `401` with `WWW-Authenticate: Bearer`.
- **`security.warn-mode=true`**: each refusal is logged as `WARN-MODE: would refuse ...` and the request is allowed. Turn it on before enforcement to find every caller that would break.

Reads are deliberately `@authz.authenticated()` for now rather than per-resource read scopes. The CI-pipeline key preset carries no read scopes, and the CLI and `setup-factstore` action read trails and flows, so tightening reads needs those clients checked first. That is tracked on #155.

<!-- matrix:begin -->
| Method | Path | Rule |
|---|---|---|
| `POST` | `/api/v1/api-keys` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/api-keys/owners/{ownerId}` | `hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#ownerId)` |
| `GET` | `/api/v1/api-keys/scopes` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/api-keys/{id}/revoke` | `hasAuthority('SCOPE_admin')` |
| `POST` | `/api/v1/api-keys/{id}/rotate` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/approvals` | `@authz.authenticated()` |
| `GET` | `/api/v1/approvals/{approvalId}` | `@authz.authenticated()` |
| `POST` | `/api/v1/approvals/{approvalId}/approve` | `@authz.has('approvals:write')` |
| `POST` | `/api/v1/approvals/{approvalId}/reject` | `@authz.has('approvals:write')` |
| `GET` | `/api/v1/artifacts` | `@authz.authenticated()` |
| `GET` | `/api/v1/artifacts/search` | `@authz.authenticated()` |
| `GET` | `/api/v1/artifacts/{artifactId}/attestations` | `@authz.authenticated()` |
| `POST` | `/api/v1/artifacts/{artifactId}/attestations` | `@authz.has('attestations:write')` |
| `GET` | `/api/v1/artifacts/{artifactId}/audit-package` | `@authz.authenticated()` |
| `GET` | `/api/v1/artifacts/{sha256}/provenance` | `@authz.authenticated()` |
| `POST` | `/api/v1/assert` | `@authz.has('assert:execute')` |
| `GET` | `/api/v1/attestation-types` | `@authz.authenticated()` |
| `POST` | `/api/v1/attestation-types` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/attestation-types/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/attestation-types/{id}` | `@authz.has('flows:write')` |
| `POST` | `/api/v1/attestation-types/{id}/archive` | `@authz.has('flows:write')` |
| `POST` | `/api/v1/attestation-types/{id}/unarchive` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/attestations/{attestationId}/audit-package` | `@authz.authenticated()` |
| `GET` | `/api/v1/attestations/{attestationId}/evidence` | `@authz.authenticated()` |
| `POST` | `/api/v1/attestations/{id}/override` | `@authz.has('admin')` |
| `GET` | `/api/v1/audit` | `@authz.authenticated()` |
| `GET` | `/api/v1/audit/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v1/auth/config` | `public: a client must learn whether sign-in is required before it has a credential` |
| `POST` | `/api/v1/auth/logout` | `public: must clear the cookie even for an expired or revoked session` |
| `GET` | `/api/v1/auth/me` | `@authz.authenticated()` |
| `POST` | `/api/v1/auth/organisation` | `@authz.authenticated()` |
| `POST` | `/api/v1/auth/refresh` | `@authz.authenticated()` |
| `GET` | `/api/v1/auth/sessions` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/auth/users/{userId}/sessions` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/auth/users/{userId}/sessions` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/compliance/artifact/{sha256Digest}` | `@authz.authenticated()` |
| `POST` | `/api/v1/compliance/assess` | `@authz.has('assert:execute')` |
| `GET` | `/api/v1/compliance/assessments` | `@authz.authenticated()` |
| `GET` | `/api/v1/compliance/assessments/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v1/compliance/mappings` | `@authz.authenticated()` |
| `POST` | `/api/v1/compliance/mappings` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/dashboard/stats` | `@authz.authenticated()` |
| `GET` | `/api/v1/deployment-policies` | `@authz.authenticated()` |
| `POST` | `/api/v1/deployment-policies` | `@authz.has('policies:write')` |
| `DELETE` | `/api/v1/deployment-policies/{id}` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/deployment-policies/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/deployment-policies/{id}` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/environments` | `@authz.authenticated()` |
| `POST` | `/api/v1/environments` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/environments/live-artifacts` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/environments/{id}` | `@authz.has('admin')` |
| `GET` | `/api/v1/environments/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/environments/{id}` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/environments/{id}/allowlist` | `@authz.authenticated()` |
| `POST` | `/api/v1/environments/{id}/allowlist` | `@authz.has('policies:write')` |
| `DELETE` | `/api/v1/environments/{id}/allowlist/{entryId}` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/environments/{id}/audit-log/csv` | `@authz.authenticated()` |
| `POST` | `/api/v1/environments/{id}/baselines` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/environments/{id}/baselines/current` | `@authz.authenticated()` |
| `GET` | `/api/v1/environments/{id}/diff` | `@authz.authenticated()` |
| `GET` | `/api/v1/environments/{id}/drift` | `@authz.authenticated()` |
| `GET` | `/api/v1/environments/{id}/drift/history` | `@authz.authenticated()` |
| `GET` | `/api/v1/environments/{id}/snapshots` | `@authz.authenticated()` |
| `POST` | `/api/v1/environments/{id}/snapshots` | `@authz.has('artifacts:write')` |
| `GET` | `/api/v1/environments/{id}/snapshots/latest` | `@authz.authenticated()` |
| `GET` | `/api/v1/environments/{id}/snapshots/{snapshotIndex}` | `@authz.authenticated()` |
| `POST` | `/api/v1/evidence/collect` | `@authz.has('evidence:write')` |
| `GET` | `/api/v1/evidence/gaps` | `@authz.authenticated()` |
| `GET` | `/api/v1/evidence/{sha256Hash}` | `@authz.authenticated()` |
| `GET` | `/api/v1/flows` | `@authz.authenticated()` |
| `POST` | `/api/v1/flows` | `hasAuthority('SCOPE_flows:write')` |
| `GET` | `/api/v1/flows/{flowId}/security-thresholds` | `@authz.authenticated()` |
| `POST` | `/api/v1/flows/{flowId}/security-thresholds` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/flows/{flowId}/trails` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/flows/{id}` | `hasAuthority('SCOPE_flows:write')` |
| `GET` | `/api/v1/flows/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/flows/{id}` | `hasAuthority('SCOPE_flows:write')` |
| `POST` | `/api/v1/flows/{id}/archive` | `hasAuthority('SCOPE_flows:write')` |
| `GET` | `/api/v1/flows/{id}/impact` | `@authz.authenticated()` |
| `POST` | `/api/v1/flows/{id}/rename` | `hasAuthority('SCOPE_flows:write')` |
| `GET` | `/api/v1/flows/{id}/template` | `@authz.authenticated()` |
| `GET` | `/api/v1/flows/{id}/template-drift` | `@authz.authenticated()` |
| `POST` | `/api/v1/flows/{id}/unarchive` | `hasAuthority('SCOPE_flows:write')` |
| `GET` | `/api/v1/frameworks` | `@authz.authenticated()` |
| `POST` | `/api/v1/frameworks` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/frameworks/{id}` | `@authz.authenticated()` |
| `POST` | `/api/v1/frameworks/{id}/controls` | `@authz.has('policies:write')` |
| `POST` | `/api/v1/gate/evaluate` | `@authz.has('assert:execute')` |
| `GET` | `/api/v1/gate/results` | `@authz.authenticated()` |
| `GET` | `/api/v1/hub/templates` | `@authz.authenticated()` |
| `POST` | `/api/v1/hub/templates/compose` | `@authz.authenticated()` |
| `GET` | `/api/v1/hub/templates/custom` | `@authz.authenticated()` |
| `POST` | `/api/v1/hub/templates/custom` | `@authz.has('flows:write')` |
| `DELETE` | `/api/v1/hub/templates/custom/{id}` | `@authz.has('flows:write')` |
| `PUT` | `/api/v1/hub/templates/custom/{id}` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/hub/templates/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v1/integrations/confluence/config` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/confluence/config` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/confluence/test` | `@authz.has('admin')` |
| `GET` | `/api/v1/integrations/jira/config` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/jira/config` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/jira/sync` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/jira/test` | `@authz.has('admin')` |
| `GET` | `/api/v1/integrations/jira/tickets` | `@authz.has('admin')` |
| `POST` | `/api/v1/integrations/jira/tickets` | `@authz.has('admin')` |
| `GET` | `/api/v1/ledger/entries` | `@authz.authenticated()` |
| `GET` | `/api/v1/ledger/entries/{recordId}` | `@authz.authenticated()` |
| `GET` | `/api/v1/ledger/status` | `@authz.authenticated()` |
| `POST` | `/api/v1/ledger/verify-chain` | `@authz.authenticated()` |
| `POST` | `/api/v1/ledger/verify/{recordId}` | `@authz.authenticated()` |
| `GET` | `/api/v1/logical-environments` | `@authz.authenticated()` |
| `POST` | `/api/v1/logical-environments` | `@authz.has('flows:write')` |
| `DELETE` | `/api/v1/logical-environments/{id}` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/logical-environments/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/logical-environments/{id}` | `@authz.has('flows:write')` |
| `DELETE` | `/api/v1/logical-environments/{id}/members/{physicalEnvId}` | `@authz.has('flows:write')` |
| `POST` | `/api/v1/logical-environments/{id}/members/{physicalEnvId}` | `@authz.has('flows:write')` |
| `GET` | `/api/v1/logical-environments/{id}/snapshots/latest` | `@authz.authenticated()` |
| `GET` | `/api/v1/metrics/compliance` | `@authz.authenticated()` |
| `GET` | `/api/v1/metrics/delivery` | `@authz.authenticated()` |
| `GET` | `/api/v1/metrics/security` | `@authz.authenticated()` |
| `GET` | `/api/v1/notification-rules` | `@authz.authenticated()` |
| `POST` | `/api/v1/notification-rules` | `@authz.has('admin')` |
| `DELETE` | `/api/v1/notification-rules/{id}` | `@authz.has('admin')` |
| `GET` | `/api/v1/notification-rules/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/notification-rules/{id}` | `@authz.has('admin')` |
| `GET` | `/api/v1/notification-rules/{id}/deliveries` | `@authz.authenticated()` |
| `POST` | `/api/v1/notification-rules/{id}/test` | `@authz.has('admin')` |
| `GET` | `/api/v1/notifications` | `@authz.authenticated()` |
| `POST` | `/api/v1/notifications/read-all` | `@authz.authenticated()` |
| `GET` | `/api/v1/notifications/unread-count` | `@authz.authenticated()` |
| `POST` | `/api/v1/notifications/{id}/read` | `@authz.authenticated()` |
| `GET` | `/api/v1/opa/bundles` | `@authz.authenticated()` |
| `POST` | `/api/v1/opa/bundles` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/opa/bundles/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/opa/bundles/{id}/activate` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/opa/decisions` | `@authz.authenticated()` |
| `GET` | `/api/v1/opa/decisions/{id}` | `@authz.authenticated()` |
| `POST` | `/api/v1/opa/evaluate` | `@authz.has('assert:execute')` |
| `GET` | `/api/v1/organisations` | `@authz.authenticated()` |
| `POST` | `/api/v1/organisations` | `@authz.has('admin')` |
| `DELETE` | `/api/v1/organisations/{slug}` | `hasAnyRole('ADMIN', 'API_USER')` |
| `GET` | `/api/v1/organisations/{slug}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/organisations/{slug}` | `@authz.has('admin')` |
| `GET` | `/api/v1/organisations/{slug}/flows` | `@authz.authenticated()` |
| `GET` | `/api/v1/organisations/{slug}/members` | `@authz.authenticated()` |
| `POST` | `/api/v1/organisations/{slug}/members` | `hasAuthority('SCOPE_admin')` |
| `DELETE` | `/api/v1/organisations/{slug}/members/{userId}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/organisations/{slug}/members/{userId}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/organisations/{slug}/members/{userId}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/organisations/{slug}/scm-integrations` | `@authz.authenticated()` |
| `POST` | `/api/v1/organisations/{slug}/scm-integrations` | `@authz.has('admin')` |
| `DELETE` | `/api/v1/organisations/{slug}/scm-integrations/{provider}` | `@authz.has('admin')` |
| `DELETE` | `/api/v1/organisations/{slug}/slack` | `@authz.has('admin')` |
| `GET` | `/api/v1/organisations/{slug}/slack` | `@authz.has('admin')` |
| `POST` | `/api/v1/organisations/{slug}/slack` | `@authz.has('admin')` |
| `POST` | `/api/v1/organisations/{slug}/slack/actions` | `public: Slack interactive action, authenticated by X-Slack-Signature` |
| `POST` | `/api/v1/organisations/{slug}/slack/commands` | `public: Slack slash command, authenticated by X-Slack-Signature` |
| `POST` | `/api/v1/organisations/{slug}/slack/notify/approval-requested` | `@authz.has('trails:write')` |
| `POST` | `/api/v1/organisations/{slug}/slack/notify/trail-non-compliant` | `@authz.has('trails:write')` |
| `DELETE` | `/api/v1/organisations/{slug}/sso` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/organisations/{slug}/sso` | `@authz.authenticated()` |
| `POST` | `/api/v1/organisations/{slug}/sso` | `hasAuthority('SCOPE_admin')` |
| `PUT` | `/api/v1/organisations/{slug}/sso` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/organisations/{slug}/sso/callback` | `public: sign-in callback; the ID token is verified against the IdP's JWKS` |
| `GET` | `/api/v1/organisations/{slug}/sso/login` | `public: sign-in entry point` |
| `POST` | `/api/v1/organisations/{slug}/sso/test` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/policies` | `@authz.authenticated()` |
| `POST` | `/api/v1/policies` | `hasAuthority('SCOPE_policies:write')` |
| `DELETE` | `/api/v1/policies/{id}` | `hasAuthority('SCOPE_policies:write')` |
| `GET` | `/api/v1/policies/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/policies/{id}` | `hasAuthority('SCOPE_policies:write')` |
| `GET` | `/api/v1/policies/{id}/versions` | `@authz.authenticated()` |
| `PUT` | `/api/v1/policies/{id}/wasm` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/policy-attachments` | `@authz.authenticated()` |
| `POST` | `/api/v1/policy-attachments` | `@authz.has('policies:write')` |
| `DELETE` | `/api/v1/policy-attachments/{id}` | `@authz.has('policies:write')` |
| `GET` | `/api/v1/policy-attachments/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v1/reports/audit-trail/{trailId}` | `@authz.authenticated()` |
| `GET` | `/api/v1/reports/compliance` | `@authz.authenticated()` |
| `GET` | `/api/v1/reports/regulatory/{frameworkId}` | `@authz.authenticated()` |
| `GET` | `/api/v1/search` | `@authz.authenticated()` |
| `POST` | `/api/v1/search` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/secrets/{path}` | `@authz.has('admin')` |
| `GET` | `/api/v1/secrets/{path}` | `@authz.has('admin')` |
| `PUT` | `/api/v1/secrets/{path}` | `@authz.has('admin')` |
| `GET` | `/api/v1/security-scans/summary` | `@authz.authenticated()` |
| `GET` | `/api/v1/security-scans/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v1/service-accounts` | `@authz.authenticated()` |
| `POST` | `/api/v1/service-accounts` | `hasAuthority('SCOPE_admin')` |
| `DELETE` | `/api/v1/service-accounts/{id}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/service-accounts/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v1/service-accounts/{id}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/service-accounts/{id}/api-keys` | `@authz.authenticated()` |
| `POST` | `/api/v1/service-accounts/{id}/api-keys` | `hasAuthority('SCOPE_admin')` |
| `DELETE` | `/api/v1/service-accounts/{id}/api-keys/{keyId}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/trails` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails` | `@authz.has('trails:write')` |
| `POST` | `/api/v1/trails/cleanup` | `@authz.has('admin')` |
| `GET` | `/api/v1/trails/lookup` | `@authz.authenticated()` |
| `DELETE` | `/api/v1/trails/{id}` | `@authz.has('admin')` |
| `GET` | `/api/v1/trails/{id}` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{id}/archive` | `@authz.has('trails:write')` |
| `GET` | `/api/v1/trails/{id}/audit` | `@authz.authenticated()` |
| `GET` | `/api/v1/trails/{id}/cascade` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{id}/unarchive` | `@authz.has('trails:write')` |
| `GET` | `/api/v1/trails/{trailId}/approvals` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/approvals` | `@authz.hasAny('approvals:write', 'trails:write')` |
| `GET` | `/api/v1/trails/{trailId}/artifacts` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/artifacts` | `@authz.has('artifacts:write')` |
| `GET` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance` | `@authz.has('artifacts:write')` |
| `POST` | `/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance/verify` | `@authz.has('artifacts:write')` |
| `POST` | `/api/v1/trails/{trailId}/assert` | `@authz.has('assert:execute')` |
| `GET` | `/api/v1/trails/{trailId}/attestations` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/attestations` | `@authz.has('attestations:write')` |
| `POST` | `/api/v1/trails/{trailId}/attestations/pull-request` | `@authz.has('attestations:write')` |
| `POST` | `/api/v1/trails/{trailId}/attestations/{attestationId}/evidence` | `@authz.has('evidence:write')` |
| `GET` | `/api/v1/trails/{trailId}/audit-package` | `@authz.authenticated()` |
| `GET` | `/api/v1/trails/{trailId}/audit-package/zip` | `@authz.authenticated()` |
| `GET` | `/api/v1/trails/{trailId}/coverage` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/coverage` | `@authz.has('evidence:write')` |
| `GET` | `/api/v1/trails/{trailId}/evidence` | `@authz.authenticated()` |
| `GET` | `/api/v1/trails/{trailId}/evidence-summary` | `@authz.authenticated()` |
| `GET` | `/api/v1/trails/{trailId}/security-scans` | `@authz.authenticated()` |
| `POST` | `/api/v1/trails/{trailId}/security-scans` | `@authz.has('attestations:write')` |
| `GET` | `/api/v1/users` | `hasAuthority('SCOPE_admin')` |
| `POST` | `/api/v1/users` | `hasAuthority('SCOPE_admin')` |
| `DELETE` | `/api/v1/users/{id}` | `hasAuthority('SCOPE_admin')` |
| `GET` | `/api/v1/users/{id}` | `hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#id)` |
| `PUT` | `/api/v1/users/{id}` | `hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#id)` |
| `GET` | `/api/v1/webhook-configs` | `@authz.has('admin')` |
| `POST` | `/api/v1/webhook-configs` | `@authz.has('admin')` |
| `DELETE` | `/api/v1/webhook-configs/{id}` | `@authz.has('admin')` |
| `GET` | `/api/v1/webhook-configs/{id}/deliveries` | `@authz.has('admin')` |
| `POST` | `/api/v1/webhooks/{source}` | `public: inbound SCM webhook, authenticated by its HMAC signature` |
| `GET` | `/api/v2/artifacts` | `@authz.authenticated()` |
| `GET` | `/api/v2/artifacts/{sha256}/provenance` | `@authz.authenticated()` |
| `POST` | `/api/v2/attestations/oidc` | `@authz.has('attestations:write')` |
| `POST` | `/api/v2/attestations/{org}/{flow}/{trail}/{artifactFingerprint}` | `@authz.has('attestations:write')` |
| `GET` | `/api/v2/flows` | `@authz.authenticated()` |
| `POST` | `/api/v2/flows` | `@authz.has('flows:write')` |
| `GET` | `/api/v2/flows/{flowId}/trails` | `@authz.authenticated()` |
| `DELETE` | `/api/v2/flows/{id}` | `@authz.has('flows:write')` |
| `GET` | `/api/v2/flows/{id}` | `@authz.authenticated()` |
| `PUT` | `/api/v2/flows/{id}` | `@authz.has('flows:write')` |
| `GET` | `/api/v2/flows/{id}/template` | `@authz.authenticated()` |
| `GET` | `/api/v2/trails` | `@authz.authenticated()` |
| `POST` | `/api/v2/trails` | `@authz.has('trails:write')` |
| `GET` | `/api/v2/trails/{id}` | `@authz.authenticated()` |
| `GET` | `/api/v2/trails/{id}/audit` | `@authz.authenticated()` |
| `GET` | `/api/v2/trails/{trailId}/artifacts` | `@authz.authenticated()` |
| `POST` | `/api/v2/trails/{trailId}/artifacts` | `@authz.has('artifacts:write')` |
| `GET` | `/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance` | `@authz.authenticated()` |
| `POST` | `/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance` | `@authz.has('artifacts:write')` |
| `POST` | `/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance/verify` | `@authz.has('artifacts:write')` |
| `GET` | `/api/v2/trails/{trailId}/attestations` | `@authz.authenticated()` |
| `POST` | `/api/v2/trails/{trailId}/attestations` | `@authz.has('attestations:write')` |
| `POST` | `/api/v2/trails/{trailId}/attestations/pull-request` | `@authz.has('attestations:write')` |
| `POST` | `/api/v2/trails/{trailId}/attestations/{attestationId}/evidence` | `@authz.has('evidence:write')` |
<!-- matrix:end -->
