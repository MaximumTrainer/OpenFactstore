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
