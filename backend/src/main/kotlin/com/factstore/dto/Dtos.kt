package com.factstore.dto

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.DeliveryStatus
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
    val storedAt: Instant
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
