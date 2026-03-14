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
  provenanceStatus: 'NO_PROVENANCE' | 'PROVENANCE_UNVERIFIED' | 'PROVENANCE_VERIFIED'
}

export type BuilderType = 'GITHUB_ACTIONS' | 'JENKINS' | 'CIRCLE_CI' | 'GENERIC'
export type SlsaLevel = 'L0' | 'L1' | 'L2' | 'L3'
export type ProvenanceStatus = 'NO_PROVENANCE' | 'PROVENANCE_UNVERIFIED' | 'PROVENANCE_VERIFIED'

export interface BuildProvenance {
  id: string
  artifactId: string
  builderId: string
  builderType: BuilderType
  buildConfigUri?: string | null
  sourceRepositoryUri?: string | null
  sourceCommitSha?: string | null
  buildStartedOn?: string | null
  buildFinishedOn?: string | null
  provenanceSignature?: string | null
  slsaLevel: SlsaLevel
  provenanceStatus: ProvenanceStatus
  recordedAt: string
}

export interface ProvenanceVerification {
  artifactId: string
  provenanceStatus: ProvenanceStatus
  message: string
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

// Service Account management
export interface ServiceAccount {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateServiceAccountRequest {
  name: string
  description?: string
}

export interface UpdateServiceAccountRequest {
  name?: string
  description?: string
}

// API Key management
export type OwnerType = 'USER' | 'SERVICE_ACCOUNT'

export interface ApiKey {
  id: string
  ownerId: string
  ownerType: OwnerType
  label: string
  /** First 12 characters of the key — safe to display for identification. */
  keyPrefix: string
  isActive: boolean
  createdAt: string
  lastUsedAt: string | null
  ttlDays: number | null
  expiresAt: string | null
}

/**
 * Returned only at creation time; includes the plain-text key shown exactly once.
 * The caller must store it securely — it cannot be retrieved again.
 */
export interface ApiKeyCreated {
  id: string
  ownerId: string
  ownerType: OwnerType
  label: string
  keyPrefix: string
  isActive: boolean
  createdAt: string
  lastUsedAt: string | null
  ttlDays: number | null
  expiresAt: string | null
  plainTextKey: string
}

export interface CreateApiKeyRequest {
  ownerId: string
  label: string
  ownerType: OwnerType
  ttlDays?: number
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

// Environment Tracking
export type EnvironmentType = 'K8S' | 'S3' | 'LAMBDA' | 'GENERIC'

export interface Environment {
  id: string
  name: string
  type: EnvironmentType
  description: string
  createdAt: string
  updatedAt: string
}

export interface SnapshotArtifact {
  artifactSha256: string
  artifactName: string
  artifactTag: string
  instanceCount: number
}

export interface EnvironmentSnapshot {
  id: string
  environmentId: string
  snapshotIndex: number
  recordedAt: string
  recordedBy: string
  artifacts: SnapshotArtifact[]
}

export interface CreateEnvironmentRequest {
  name: string
  type: EnvironmentType
  description?: string
}

export interface UpdateEnvironmentRequest {
  name?: string
  type?: EnvironmentType
  description?: string
}

export interface RecordSnapshotRequest {
  recordedBy: string
  artifacts: Array<{
    artifactSha256: string
    artifactName: string
    artifactTag: string
    instanceCount: number
  }>
}


// Notification types
export type TriggerEvent =
  | 'ATTESTATION_FAILED'
  | 'GATE_BLOCKED'
  | 'DRIFT_DETECTED'
  | 'APPROVAL_REQUIRED'
  | 'TRAIL_NON_COMPLIANT'
  | 'APPROVAL_REJECTED'

export type ChannelType = 'SLACK' | 'WEBHOOK' | 'IN_APP'

export type NotificationDeliveryStatus = 'SENT' | 'FAILED' | 'SKIPPED'

export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL'

export interface NotificationRule {
  id: string
  name: string
  isActive: boolean
  triggerEvent: TriggerEvent
  channelType: ChannelType
  channelConfig: string
  filterFlowId: string | null
  filterEnvironmentId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateNotificationRuleRequest {
  name: string
  triggerEvent: TriggerEvent
  channelType: ChannelType
  channelConfig?: string
  filterFlowId?: string | null
  filterEnvironmentId?: string | null
}

export interface UpdateNotificationRuleRequest {
  name?: string
  isActive?: boolean
  triggerEvent?: TriggerEvent
  channelType?: ChannelType
  channelConfig?: string
  filterFlowId?: string | null
  filterEnvironmentId?: string | null
  clearFilterFlowId?: boolean
  clearFilterEnvironmentId?: boolean
}

export interface NotificationDelivery {
  id: string
  ruleId: string
  eventType: string
  payload: string | null
  status: NotificationDeliveryStatus
  sentAt: string
  error: string | null
  attemptCount: number
}

export interface Notification {
  id: string
  title: string
  message: string
  severity: NotificationSeverity
  isRead: boolean
  entityType: string | null
  entityId: string | null
  createdAt: string
}

// Evidence Collection types
export interface ReportCoverageRequest {
  tool: string
  lineCoverage?: number
  branchCoverage?: number
  minCoverage?: number
  reportFileName?: string
  details?: string
}

export interface CoverageReport {
  id: string
  trailId: string
  tool: string
  lineCoverage: number | null
  branchCoverage: number | null
  minCoverage: number | null
  passed: boolean
  reportFileName: string | null
  reportFileHash: string | null
  details: string | null
  createdAt: string
}

export interface BulkEvidenceItem {
  trailId: string
  evidenceType: string
  tool: string
  passed: boolean
  details?: string
}

export interface BulkEvidenceRequest {
  items: BulkEvidenceItem[]
}

export interface BulkEvidenceResult {
  trailId: string
  evidenceType: string
  attestationId: string
  passed: boolean
}

export interface BulkEvidenceResponse {
  results: BulkEvidenceResult[]
  accepted: number
  failed: number
}

export interface EvidenceSummary {
  trailId: string
  collectedTypes: string[]
  coverageReports: CoverageReport[]
  totalAttestations: number
  passedAttestations: number
  failedAttestations: number
  pendingAttestations: number
  isComplete: boolean
  missingRequiredTypes: string[]
}

export interface EvidenceGapItem {
  trailId: string
  gitCommitSha: string
  gitBranch: string
  flowId: string
  missingTypes: string[]
  trailStatus: 'PENDING' | 'COMPLIANT' | 'NON_COMPLIANT'
}

export interface EvidenceGapsResponse {
  gaps: EvidenceGapItem[]
  totalTrailsWithGaps: number
}

export type SsoProvider = 'ENTRA_ID' | 'OKTA'

export interface SsoConfig {
  id: string
  orgSlug: string
  provider: SsoProvider
  issuerUrl: string
  clientId: string
  attributeMappings: string
  groupRoleMappings: string
  isMandatory: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateSsoConfigRequest {
  provider: SsoProvider
  issuerUrl: string
  clientId: string
  clientSecret?: string
  attributeMappings?: string
  groupRoleMappings?: string
  isMandatory?: boolean
}

export interface UpdateSsoConfigRequest {
  provider?: SsoProvider
  issuerUrl?: string
  clientId?: string
  clientSecret?: string
  attributeMappings?: string
  groupRoleMappings?: string
  isMandatory?: boolean
}

export interface SsoTestConnectionResponse {
  success: boolean
  message: string
  authorizationEndpoint?: string
  tokenEndpoint?: string
}

export interface SsoLoginUrlResponse {
  loginUrl: string
  state: string
}

export interface SsoCallbackResponse {
  token: string
  userId: string
  email: string
  name: string
}

// Vault Evidence types
export interface VaultEvidenceResponse {
  entityType: string
  entityId: string
  evidenceType: string
  vaultPath: string
  version: number
  data?: Record<string, string> | null
  storedAt: string
}

export interface VaultEvidenceListResponse {
  entityType: string
  entityId: string
  evidenceTypes: string[]
}

export interface VaultHealthResponse {
  healthy: boolean
  vaultUri: string
  authMethod: string
  message: string
  checkedAt: string
}

export interface StoreEvidenceRequest {
  evidenceType: string
  data: Record<string, string>
}
