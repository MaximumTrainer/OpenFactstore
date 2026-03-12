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
