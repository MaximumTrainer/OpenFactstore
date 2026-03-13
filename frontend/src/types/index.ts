export interface Flow {
  id: string
  name: string
  description: string
  requiredAttestationTypes: string[]
  createdAt: string
  updatedAt: string
}

export interface Trail {
  id: string
  flowId: string
  flowName?: string
  gitCommitSha: string
  gitBranch: string
  gitAuthor: string
  gitAuthorEmail: string
  pullRequestId?: string
  pullRequestReviewer?: string
  deploymentActor?: string
  status: 'PENDING' | 'COMPLIANT' | 'NON_COMPLIANT'
  createdAt: string
  updatedAt: string
}

export interface Attestation {
  id: string
  trailId: string
  type: string
  status: 'PASSED' | 'FAILED' | 'PENDING'
  evidenceFileName?: string
  evidenceFileHash?: string
  details?: string
  createdAt: string
}

export interface Artifact {
  id: string
  trailId: string
  imageName: string
  imageTag: string
  sha256Digest: string
  registry?: string
  reportedAt: string
  reportedBy: string
}

export interface EvidenceFile {
  id: string
  attestationId: string
  fileName: string
  sha256Hash: string
  fileSizeBytes: number
  contentType: string
  storedAt: string
  /** Non-null when the evidence binary is stored at an external location. */
  externalUrl?: string | null
}

export interface AssertResult {
  compliant: boolean
  sha256Digest: string
  flowId: string
  flowName: string
  message: string
  missingAttestations: string[]
  failedAttestations: string[]
}

export interface WebhookConfig {
  id: string
  source: 'GITHUB' | 'JENKINS' | 'CIRCLECI' | 'GITLAB' | 'GENERIC'
  flowId: string
  isActive: boolean
  createdAt: string
}

export interface WebhookDelivery {
  id: string
  webhookConfigId: string
  deliveryId: string
  source: 'GITHUB' | 'JENKINS' | 'CIRCLECI' | 'GITLAB' | 'GENERIC'
  eventType: string | null
  status: 'SUCCESS' | 'FAILED' | 'DUPLICATE'
  statusMessage: string | null
  receivedAt: string
}

export interface JiraConfig {
  id: string
  jiraBaseUrl: string
  jiraUsername: string
  defaultProjectKey: string
  createdAt: string
  updatedAt: string
}

export interface ConfluenceConfig {
  id: string
  confluenceBaseUrl: string
  confluenceUsername: string
  defaultSpaceKey: string
  createdAt: string
  updatedAt: string
}

export interface JiraTicket {
  id: string
  ticketKey: string
  summary: string
  status: string
  issueType: string
  projectKey: string
  trailId: string | null
  createdAt: string
}

export interface ConnectionTestResponse {
  success: boolean
  message: string
}

export interface JiraSyncResponse {
  syncedCount: number
  message: string
}

// User management
export interface User {
  id: string
  email: string
  name: string
  /** GitHub user ID (set when the user authenticated via GitHub OAuth). */
  githubId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  email: string
  name: string
  githubId?: string
}

export interface UpdateUserRequest {
  name?: string
  githubId?: string
}

// API Key management
export type ApiKeyType = 'PERSONAL' | 'SERVICE'

export interface ApiKey {
  id: string
  userId: string
  name: string
  type: ApiKeyType
  /** First 12 characters of the key — safe to display for identification. */
  keyPrefix: string
  isActive: boolean
  createdAt: string
  lastUsedAt: string | null
}

/**
 * Returned only at creation time; includes the plain-text key shown exactly once.
 * The caller must store it securely — it cannot be retrieved again.
 * Note: `lastUsedAt` is always null on creation.
 */
export interface ApiKeyCreated {
  id: string
  userId: string
  name: string
  type: ApiKeyType
  /** First 12 characters of the key — safe to display for identification. */
  keyPrefix: string
  isActive: boolean
  createdAt: string
  lastUsedAt: string | null
  plainTextKey: string
}

export interface CreateApiKeyRequest {
  userId: string
  name: string
  type: ApiKeyType
}

// Audit Log
export type AuditEventType =
  | 'ARTIFACT_DEPLOYED'
  | 'ARTIFACT_REMOVED'
  | 'ARTIFACT_UPDATED'
  | 'ENVIRONMENT_CREATED'
  | 'ENVIRONMENT_DELETED'
  | 'POLICY_EVALUATED'
  | 'ATTESTATION_RECORDED'
  | 'APPROVAL_GRANTED'
  | 'APPROVAL_REJECTED'
  | 'GATE_BLOCKED'
  | 'GATE_ALLOWED'

export interface AuditEvent {
  id: string
  eventType: AuditEventType
  environmentId: string | null
  trailId: string | null
  artifactSha256: string | null
  actor: string
  payload: string
  occurredAt: string
}

export interface AuditEventPage {
  events: AuditEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Ledger types
export interface LedgerEntry {
  entryId: string
  factId: string
  eventType: string
  contentHash: string
  previousHash: string
  timestamp: string
  metadata: Record<string, string>
}

export interface PagedLedgerEntries {
  entries: LedgerEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface LedgerVerification {
  factId: string
  verified: boolean
  contentHash: string | null
  chainPosition: number | null
  previousHash: string | null
  ledgerTimestamp: string | null
  verifiedAt: string
  message: string
}

export interface ChainVerification {
  valid: boolean
  entriesChecked: number
  firstEntryTimestamp: string | null
  lastEntryTimestamp: string | null
  brokenAt: string | null
  message: string
}

export interface LedgerStatus {
  enabled: boolean
  type: string
  totalEntries: number
  healthy: boolean
  message: string
}
// Search types
export interface SearchResultItem {
  type: 'trail' | 'artifact'
  id: string
  title: string
  description: string
  metadata: Record<string, string | null>
}

export interface SearchResponse {
  results: SearchResultItem[]
  total: number
  query: string
  type: string | null
}

// Dashboard stats
export interface DashboardStats {
  totalFlows: number
  totalTrails: number
  compliantTrails: number
  nonCompliantTrails: number
  pendingTrails: number
  complianceRate: number
}

// Compliance report
export interface TrailComplianceSummary {
  id: string
  gitCommitSha: string
  gitBranch: string
  gitAuthor: string
  status: string
  createdAt: string
}

export interface FlowComplianceReport {
  flowId: string | null
  flowName: string
  from: string | null
  to: string | null
  totalTrails: number
  compliantTrails: number
  nonCompliantTrails: number
  pendingTrails: number
  complianceRate: number
  nonCompliantTrailList: TrailComplianceSummary[]
}

// Audit trail export
export interface AuditTrailExport {
  trailId: string
  exportedAt: string
  trail: Trail
  flow: Flow
  artifacts: Artifact[]
  attestations: Attestation[]
  evidenceFiles: EvidenceFile[]
}

