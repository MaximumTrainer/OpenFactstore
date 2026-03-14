# Part 10: Approvals

An **Approval** is a formal sign-off that a release is safe to proceed. When an artifact is ready to be deployed to a specific environment, an operator or automated system requests an approval. The release cannot proceed to that environment (via a policy with `enforceApprovals: true`) until the approval is granted.

Approvals close the gap between CI/CD automation and human governance — sign-offs done outside the system leave no audit trail. Factstore records every approval decision so it is provably auditable.

---

## Approval states

| State | Meaning |
|---|---|
| `PENDING_APPROVAL` | Approval has been requested; awaiting decision |
| `APPROVED` | All required approvers have approved |
| `REJECTED` | One approver has rejected — release is blocked |
| `EXPIRED` | The approval deadline passed without a decision |

---

## Request an approval

```bash
curl -s -X POST "$BASE_URL/api/v1/trails/$TRAIL_ID/approvals" \
  -H "Content-Type: application/json" \
  -d '{
    "requiredApprovers": ["security-team", "qa-lead"],
    "newestCommit": "e67f2f2b121f9325ebf166b7b3c707f73cb48b14",
    "oldestCommit": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0",
    "targetEnvironment": "production",
    "deadline": "2026-03-12T10:00:00Z"
  }'
```

Store the returned `id` as `APPROVAL_ID`.

---

## Grant approval

```bash
curl -s -X POST "$BASE_URL/api/v1/approvals/$APPROVAL_ID/approve" \
  -H "Content-Type: application/json" \
  -d '{
    "approverIdentity": "jane@example.com",
    "comments": "Reviewed and approved — no concerns"
  }'
```

---

## Reject an approval

```bash
curl -s -X POST "$BASE_URL/api/v1/approvals/$APPROVAL_ID/reject" \
  -H "Content-Type: application/json" \
  -d '{
    "approverIdentity": "bob@example.com",
    "comments": "Snyk scan shows critical vulnerabilities — do not deploy"
  }'
```

---

## List pending approvals

```bash
curl -s "$BASE_URL/api/v1/approvals?status=PENDING_APPROVAL"
```

---

## Get approval details

```bash
curl -s "$BASE_URL/api/v1/approvals/$APPROVAL_ID"
```

---

## Integration with Policies

When a [Deployment Policy](./09-policies.md) is created with `enforceApprovals: true`, the deployment gate blocks any artifact whose Trail has no `APPROVED` approval.

```bash
curl -s -X POST "$BASE_URL/api/v1/policies" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-requirements",
    "flowId": "'$FLOW_ID'",
    "enforceApprovals": true
  }'
```

An artifact cannot pass the gate for an environment with this policy unless its Trail has an `APPROVED` approval.

---

## Audit trail

Every approval decision (granted, rejected, or expired) is stored as an immutable record. Compliance auditors can query the full approval history for any Trail at any time.

---

Previous: [← Part 9: Policies](./09-policies.md) | Next: [Part 11: Next Steps →](./11-next-steps.md)
