# 13 — Jira & Confluence Integration

> **Status:** 🆕 New — No existing GitHub issue

## Summary

Integrate with Atlassian Jira and Confluence to automate compliance ticket creation, approval tracking, and audit documentation. Every compliance event in the fact store can automatically create or update Jira tickets and publish compliance reports to Confluence.

## Motivation

Financial services organizations use Jira for change management and Confluence for compliance documentation. The fact store should integrate with these tools to:

- **Automate compliance ticket creation** for each release
- **Track approval workflows** in Jira alongside fact store approvals
- **Publish compliance reports** to Confluence for auditors
- **Link fact store evidence** to Jira tickets for traceability

From the financial services scenario:

> *"Jira: Automatically create a compliance ticket for each release."*
> *"Confluence: Store reports and approval logs for audits."*

## Requirements

### Jira Integration

#### Automated Ticket Creation

When specific events occur in the fact store, automatically create Jira tickets:

| Fact Store Event | Jira Action |
|---|---|
| New trail created | Create "Compliance Review" task |
| Security scan failed | Create "Security Vulnerability" bug |
| Approval required | Create "Release Approval" task |
| Gate blocked | Create "Deployment Blocked" incident |
| Drift detected | Create "Unauthorized Change" incident |

#### Jira API Integration

```bash
curl -X POST -u "user:api_token" \
    -H "Content-Type: application/json" \
    --data '{
        "fields": {
            "project": {"key": "COMP"},
            "summary": "Compliance Review - Release v1.2.3",
            "description": "Security scan passed. Awaiting regulatory approval.",
            "issuetype": {"name": "Task"}
        }
    }' \
    https://company-jira.atlassian.net/rest/api/2/issue
```

#### Bidirectional Sync

- Jira ticket status updates reflected in fact store (e.g., Jira approval → fact store approval)
- Fact store links embedded in Jira ticket descriptions
- Jira webhook listener for status change events

### Confluence Integration

#### Automated Report Publishing

- Publish compliance summary reports to Confluence spaces
- Auto-generate release compliance pages with evidence links
- Update existing pages when new evidence is recorded

#### Report Templates

- Release compliance report template
- Quarterly audit summary template
- Security scan results template

### Configuration

#### Jira Configuration Entity

| Field | Description |
|---|---|
| `jiraBaseUrl` | Jira instance URL |
| `jiraUsername` | Service account username |
| `jiraApiToken` | API token (stored in Vault) |
| `defaultProjectKey` | Default Jira project for tickets |
| `ticketMappings` | Event-to-ticket-type mappings |

#### Confluence Configuration Entity

| Field | Description |
|---|---|
| `confluenceBaseUrl` | Confluence instance URL |
| `confluenceUsername` | Service account username |
| `confluenceApiToken` | API token (stored in Vault) |
| `defaultSpaceKey` | Default Confluence space for reports |

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/integrations/jira/config` | Configure Jira integration |
| GET | `/api/v1/integrations/jira/config` | Get Jira configuration |
| POST | `/api/v1/integrations/jira/test` | Test Jira connectivity |
| POST | `/api/v1/integrations/confluence/config` | Configure Confluence integration |
| GET | `/api/v1/integrations/confluence/config` | Get Confluence configuration |
| POST | `/api/v1/integrations/confluence/test` | Test Confluence connectivity |
| POST | `/api/v1/integrations/jira/sync` | Manual sync of fact store events to Jira |
| GET | `/api/v1/integrations/jira/tickets` | List Jira tickets created by fact store |

### Frontend

- Integration settings page for Jira and Confluence configuration
- Jira ticket links displayed on Trail Detail and Approval pages
- "Publish to Confluence" button on compliance reports
- Integration health status indicators

## Acceptance Criteria

- [ ] Jira REST API client implemented
- [ ] Automated ticket creation for key compliance events
- [ ] Bidirectional sync between Jira and fact store approvals
- [ ] Confluence REST API client implemented
- [ ] Automated compliance report publishing to Confluence
- [ ] Integration configuration API endpoints
- [ ] Connectivity test endpoints for both Jira and Confluence
- [ ] Credentials stored securely (Vault integration)
- [ ] Unit tests with mock Jira/Confluence APIs
- [ ] Frontend integration settings page
- [ ] OpenAPI documentation updated

## Technical Notes

### Dependencies

- Atlassian REST API client (HTTP via RestTemplate/WebClient)
- Jira REST API v2/v3
- Confluence REST API v2
