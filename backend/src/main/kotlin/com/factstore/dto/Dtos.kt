package com.factstore.dto

import com.factstore.core.domain.ApiKeyType
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.DeliveryStatus
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.TrailStatus
import com.factstore.core.domain.WebhookSource
import java.time.Instant
import java.util.UUID

// Flow DTOs
data class CreateFlowRequest(
    val name: String,
    val description: String = "",
    val requiredAttestationTypes: List<String> = emptyList()
)

data class UpdateFlowRequest(
    val name: String? = null,
    val description: String? = null,
    val requiredAttestationTypes: List<String>? = null
)

data class FlowResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val requiredAttestationTypes: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Trail DTOs
data class CreateTrailRequest(
    val flowId: UUID,
    val gitCommitSha: String,
    val gitBranch: String,
    val gitAuthor: String,
    val gitAuthorEmail: String,
    val pullRequestId: String? = null,
    val pullRequestReviewer: String? = null,
    val deploymentActor: String? = null
)

data class TrailResponse(
    val id: UUID,
    val flowId: UUID,
    val gitCommitSha: String,
    val gitBranch: String,
    val gitAuthor: String,
    val gitAuthorEmail: String,
    val pullRequestId: String?,
    val pullRequestReviewer: String?,
    val deploymentActor: String?,
    val status: TrailStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Attestation DTOs
data class CreateAttestationRequest(
    val type: String,
    val status: AttestationStatus = AttestationStatus.PENDING,
    val details: String? = null
)

data class AttestationResponse(
    val id: UUID,
    val trailId: UUID,
    val type: String,
    val status: AttestationStatus,
    val evidenceFileHash: String?,
    val evidenceFileName: String?,
    val evidenceFileSizeBytes: Long?,
    val details: String?,
    val createdAt: Instant
)

// Artifact DTOs
data class CreateArtifactRequest(
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String? = null,
    val reportedBy: String
)

data class ArtifactResponse(
    val id: UUID,
    val trailId: UUID,
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String?,
    val reportedAt: Instant,
    val reportedBy: String
)

// Evidence File DTOs
data class EvidenceFileResponse(
    val id: UUID,
    val attestationId: UUID,
    val fileName: String,
    val sha256Hash: String,
    val fileSizeBytes: Long,
    val contentType: String,
    val storedAt: Instant,
    /** Non-null when the evidence binary lives at an external location rather than inline. */
    val externalUrl: String? = null
)

// Assert DTOs
data class AssertRequest(
    val sha256Digest: String,
    val flowId: UUID
)

data class AssertResponse(
    val sha256Digest: String,
    val flowId: UUID,
    val status: ComplianceStatus,
    val missingAttestationTypes: List<String>,
    val failedAttestationTypes: List<String>,
    val details: String
)

enum class ComplianceStatus { COMPLIANT, NON_COMPLIANT }

// Chain of Custody DTO
data class ChainOfCustodyResponse(
    val artifact: ArtifactResponse,
    val trail: TrailResponse,
    val flow: FlowResponse,
    val attestations: List<AttestationResponse>,
    val evidenceFiles: List<EvidenceFileResponse>,
    val complianceStatus: ComplianceStatus
)

// Webhook Config DTOs
data class CreateWebhookConfigRequest(
    val source: WebhookSource,
    val secret: String,
    val flowId: UUID
)

data class WebhookConfigResponse(
    val id: UUID,
    val source: WebhookSource,
    val flowId: UUID,
    val isActive: Boolean,
    val createdAt: Instant
)

// Webhook Delivery DTOs
data class WebhookDeliveryResponse(
    val id: UUID,
    val webhookConfigId: UUID,
    val deliveryId: String,
    val source: WebhookSource,
    val eventType: String?,
    val status: DeliveryStatus,
    val statusMessage: String?,
    val receivedAt: Instant
)

// Webhook Inbound Event DTOs
data class GenericWebhookPayload(
    val eventType: String,
    val trailId: UUID? = null,
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    val gitAuthor: String? = null,
    val gitAuthorEmail: String? = null,
    val attestationType: String? = null,
    val attestationStatus: AttestationStatus? = null,
    val details: String? = null
)

data class WebhookResponse(
    val accepted: Boolean,
    val deliveryId: String,
    val message: String
)

// Error DTO
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)

// Jira Integration DTOs
data class JiraConfigRequest(
    val jiraBaseUrl: String,
    val jiraUsername: String,
    val jiraApiToken: String,
    val defaultProjectKey: String
)

data class JiraConfigResponse(
    val id: UUID,
    val jiraBaseUrl: String,
    val jiraUsername: String,
    val defaultProjectKey: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class JiraTicketResponse(
    val id: UUID,
    val ticketKey: String,
    val summary: String,
    val status: String,
    val issueType: String,
    val projectKey: String,
    val trailId: UUID?,
    val createdAt: Instant
)

// Confluence Integration DTOs
data class ConfluenceConfigRequest(
    val confluenceBaseUrl: String,
    val confluenceUsername: String,
    val confluenceApiToken: String,
    val defaultSpaceKey: String
)

data class ConfluenceConfigResponse(
    val id: UUID,
    val confluenceBaseUrl: String,
    val confluenceUsername: String,
    val defaultSpaceKey: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Integration connectivity test DTO
data class ConnectionTestResponse(
    val success: Boolean,
    val message: String
)

// Jira sync DTO
data class JiraSyncResponse(
    val syncedCount: Int,
    val message: String
)

// Create Jira ticket request DTO
data class CreateJiraTicketRequest(
    val trailId: UUID,
    val summary: String,
    val issueType: String = "Task"
)

// Slack Integration DTOs
data class ConfigureSlackRequest(
    val botToken: String,
    val signingSecret: String,
    val channel: String
)

data class SlackConfigResponse(
    val id: UUID,
    val orgSlug: String,
    val channel: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SlackCommandResponse(
    val responseType: String = "in_channel",
    val text: String
)

data class TrailNonCompliantNotificationRequest(
    val trailId: UUID,
    val flowName: String,
    val missingAttestationTypes: List<String>,
    val failedAttestationTypes: List<String>,
    val trailUrl: String? = null
)

data class ApprovalRequestedNotificationRequest(
    val approvalId: String,
    val artifactSha: String,
    val targetEnvironment: String,
    val requiredApprovers: List<String>
)

// Slack Notification Events
sealed class SlackNotification {
    data class TrailNonCompliant(
        val trailId: UUID,
        val flowName: String,
        val missingAttestationTypes: List<String>,
        val failedAttestationTypes: List<String>,
        val trailUrl: String? = null
    ) : SlackNotification()

    data class ApprovalRequested(
        val approvalId: String,
        val artifactSha: String,
        val targetEnvironment: String,
        val requiredApprovers: List<String>
    ) : SlackNotification()

    data class ApprovalDecision(
        val approvalId: String,
        val decision: String,
        val decidedBy: String,
        val comment: String? = null
    ) : SlackNotification()
}

// User DTOs
data class CreateUserRequest(
    val email: String,
    val name: String,
    val githubId: String? = null
)

data class UpdateUserRequest(
    val name: String? = null,
    val githubId: String? = null
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val githubId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Environment DTOs
data class CreateEnvironmentRequest(
    val name: String,
    val type: EnvironmentType,
    val description: String = ""
)

data class UpdateEnvironmentRequest(
    val name: String? = null,
    val type: EnvironmentType? = null,
    val description: String? = null
)

data class EnvironmentResponse(
    val id: UUID,
    val name: String,
    val type: EnvironmentType,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SnapshotArtifactRequest(
    val artifactSha256: String,
    val artifactName: String,
    val artifactTag: String,
    val instanceCount: Int = 1
)

data class RecordSnapshotRequest(
    val recordedBy: String,
    val artifacts: List<SnapshotArtifactRequest> = emptyList()
)

data class SnapshotArtifactResponse(
    val artifactSha256: String,
    val artifactName: String,
    val artifactTag: String,
    val instanceCount: Int
)

data class EnvironmentSnapshotResponse(
    val id: UUID,
    val environmentId: UUID,
    val snapshotIndex: Long,
    val recordedAt: Instant,
    val recordedBy: String,
    val artifacts: List<SnapshotArtifactResponse>
)

// API Key DTOs
data class CreateApiKeyRequest(
    val userId: UUID,
    val name: String,
    val type: ApiKeyType
)

data class ApiKeyResponse(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val type: ApiKeyType,
    /** First 12 characters of the key (safe to display for identification). */
    val keyPrefix: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?
)

/**
 * Returned only once at creation time; contains the plain-text key that must be
 * stored securely by the caller — it cannot be retrieved again.
 */
data class ApiKeyCreatedResponse(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val type: ApiKeyType,
    val keyPrefix: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    /** The full plain-text key. Shown exactly once; never persisted in clear text. */
    val plainTextKey: String
)
