package com.factstore.dto

import com.factstore.core.domain.AllowlistEntryStatus
import com.factstore.core.domain.ScmProvider
import com.factstore.core.domain.ApprovalDecisionType
import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.GateDecision
import com.factstore.core.domain.BuilderType
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.DeliveryStatus
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.DriftPolicy
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.NotificationDeliveryStatus
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.domain.OrgType
import com.factstore.core.domain.SsoProvider
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.domain.SlsaLevel
import com.factstore.core.domain.TrailStatus
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.domain.BundleStatus
import com.factstore.core.domain.WebhookSource
import com.factstore.core.domain.ScanType
import com.factstore.core.domain.AssessmentStatus
import java.time.Instant
import java.util.UUID

// Dry-run wrapper
data class DryRunResponse(
    val dryRun: Boolean = true,
    val wouldCreate: Any
)

// Flow DTOs
data class CreateFlowRequest(
    val name: String,
    val description: String = "",
    val requiredAttestationTypes: List<String> = emptyList(),
    val tags: Map<String, String> = emptyMap(),
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean = false,
    val requiredApproverRoles: List<String> = emptyList()
)

data class UpdateFlowRequest(
    val name: String? = null,
    val description: String? = null,
    val requiredAttestationTypes: List<String>? = null,
    val tags: Map<String, String>? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean? = null,
    val requiredApproverRoles: List<String>? = null
)

data class FlowResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val requiredAttestationTypes: List<String>,
    val tags: Map<String, String>,
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requiresApproval: Boolean = false,
    val requiredApproverRoles: List<String> = emptyList()
)

// Trail DTOs
data class CreateTrailRequest(
    val flowId: UUID,
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    val gitAuthor: String,
    val gitAuthorEmail: String,
    val pullRequestId: String? = null,
    val pullRequestReviewer: String? = null,
    val deploymentActor: String? = null,
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val buildUrl: String? = null
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
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val buildUrl: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Attestation DTOs
data class CreateAttestationRequest(
    val type: String,
    val status: AttestationStatus = AttestationStatus.PENDING,
    val details: String? = null,
    val name: String? = null,
    val evidenceUrl: String? = null,
    val orgSlug: String? = null
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
    val name: String?,
    val evidenceUrl: String?,
    val compliant: Boolean,
    val orgSlug: String? = null,
    val artifactFingerprint: String? = null,
    val createdAt: Instant
)

// Artifact DTOs
data class CreateArtifactRequest(
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String? = null,
    val reportedBy: String,
    val orgSlug: String? = null
)

data class ArtifactResponse(
    val id: UUID,
    val trailId: UUID,
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String?,
    val reportedAt: Instant,
    val reportedBy: String,
    val orgSlug: String? = null,
    val provenanceStatus: ProvenanceStatus = ProvenanceStatus.NO_PROVENANCE
)

// Build Provenance DTOs
data class RecordProvenanceRequest(
    val builderId: String,
    val builderType: BuilderType,
    val buildConfigUri: String? = null,
    val sourceRepositoryUri: String? = null,
    val sourceCommitSha: String? = null,
    val buildStartedOn: Instant? = null,
    val buildFinishedOn: Instant? = null,
    val provenanceSignature: String? = null,
    val slsaLevel: SlsaLevel = SlsaLevel.L0
)

data class BuildProvenanceResponse(
    val id: UUID,
    val artifactId: UUID,
    val builderId: String,
    val builderType: BuilderType,
    val buildConfigUri: String?,
    val sourceRepositoryUri: String?,
    val sourceCommitSha: String?,
    val buildStartedOn: Instant?,
    val buildFinishedOn: Instant?,
    val provenanceSignature: String?,
    val slsaLevel: SlsaLevel,
    val provenanceStatus: ProvenanceStatus,
    val recordedAt: Instant
)

data class ProvenanceVerificationResponse(
    val artifactId: UUID,
    val provenanceStatus: ProvenanceStatus,
    val message: String
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
    val details: String,
    val missingAttestationNames: List<String> = emptyList(),
    val failedAttestationNames: List<String> = emptyList()
)

enum class ComplianceStatus { COMPLIANT, NON_COMPLIANT }

data class FlowTemplateResponse(
    val flowId: UUID,
    val templateYaml: String?,
    val effectiveTemplate: Map<String, Any>?
)

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

// Search DTOs
data class SearchResultItem(
    val type: String,
    val id: UUID,
    val title: String,
    val description: String,
    val metadata: Map<String, String?> = emptyMap()
)

data class SearchResponse(
    val results: List<SearchResultItem>,
    val total: Int,
    val query: String,
    val type: String?
)

// Dashboard Stats DTO
data class DashboardStatsResponse(
    val totalFlows: Int,
    val totalTrails: Int,
    val compliantTrails: Int,
    val nonCompliantTrails: Int,
    val pendingTrails: Int,
    val complianceRate: Double
)

// Compliance Report DTOs
data class TrailComplianceSummary(
    val id: UUID,
    val gitCommitSha: String,
    val gitBranch: String,
    val gitAuthor: String,
    val status: String,
    val createdAt: Instant
)

data class FlowComplianceReport(
    val flowId: UUID?,
    val flowName: String,
    val from: Instant?,
    val to: Instant?,
    val totalTrails: Int,
    val compliantTrails: Int,
    val nonCompliantTrails: Int,
    val pendingTrails: Int,
    val complianceRate: Double,
    val nonCompliantTrailList: List<TrailComplianceSummary>
)

// Audit Trail Export DTO
data class AuditTrailExportResponse(
    val trailId: UUID,
    val exportedAt: Instant,
    val trail: TrailResponse,
    val flow: FlowResponse,
    val artifacts: List<ArtifactResponse>,
    val attestations: List<AttestationResponse>,
    val evidenceFiles: List<EvidenceFileResponse>
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

// Ledger DTOs
data class LedgerEntryResponse(
    val entryId: String,
    val recordId: UUID,
    val eventType: String,
    val contentHash: String,
    val previousHash: String,
    val timestamp: Instant,
    val metadata: Map<String, String>
)

data class PagedLedgerEntriesResponse(
    val entries: List<LedgerEntryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class VerificationResponse(
    val recordId: UUID,
    val verified: Boolean,
    val contentHash: String?,
    val chainPosition: Int?,
    val previousHash: String?,
    val ledgerTimestamp: Instant?,
    val verifiedAt: Instant,
    val message: String
)

data class VerifyChainRequest(
    val from: Instant,
    val to: Instant
)

data class ChainVerificationResponse(
    val valid: Boolean,
    val entriesChecked: Int,
    val firstEntryTimestamp: Instant?,
    val lastEntryTimestamp: Instant?,
    val brokenAt: String?,
    val message: String
)

data class LedgerStatusResponse(
    val enabled: Boolean,
    val type: String,
    val totalEntries: Long,
    val healthy: Boolean,
    val message: String
)

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
data class ScopeListDto(
    val names: List<String> = emptyList(),
    val patterns: List<String> = emptyList()
)

data class SnapshotScopeDto(
    val include: ScopeListDto = ScopeListDto(),
    val exclude: ScopeListDto = ScopeListDto()
)

data class CreateEnvironmentRequest(
    val name: String,
    val type: EnvironmentType,
    val description: String = "",
    val orgSlug: String? = null,
    val driftPolicy: DriftPolicy = DriftPolicy.WARN,
    val scope: SnapshotScopeDto? = null
)

data class UpdateEnvironmentRequest(
    val name: String? = null,
    val type: EnvironmentType? = null,
    val description: String? = null,
    val driftPolicy: DriftPolicy? = null,
    val scope: SnapshotScopeDto? = null
)

data class EnvironmentResponse(
    val id: UUID,
    val name: String,
    val type: EnvironmentType,
    val description: String,
    val orgSlug: String? = null,
    val driftPolicy: DriftPolicy,
    val scope: SnapshotScopeDto? = null,
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

// Environment Baseline DTOs
data class CreateBaselineRequest(
    val snapshotId: UUID? = null,
    val approvedBy: String,
    val description: String? = null
)

data class BaselineResponse(
    val id: UUID,
    val environmentId: UUID,
    val snapshotId: UUID?,
    val approvedBy: String,
    val approvedAt: Instant,
    val description: String?,
    val isActive: Boolean
)

// Drift Detection DTOs
data class SnapshotDiffEntry(
    val artifactName: String,
    val artifactTag: String,
    val sha256From: String? = null,
    val sha256To: String? = null
)

data class SnapshotDiffResponse(
    val environmentId: UUID,
    val fromSnapshotIndex: Long,
    val toSnapshotIndex: Long,
    val added: List<SnapshotDiffEntry>,
    val removed: List<SnapshotDiffEntry>,
    val updated: List<SnapshotDiffEntry>,
    val unchanged: List<SnapshotDiffEntry>
)

data class DriftReportResponse(
    val id: UUID,
    val environmentId: UUID,
    val baselineId: UUID?,
    val snapshotId: UUID,
    val generatedAt: Instant,
    val hasDrift: Boolean,
    val added: List<SnapshotDiffEntry>,
    val removed: List<SnapshotDiffEntry>,
    val updated: List<SnapshotDiffEntry>
)

// Organisation Member DTOs
data class InviteMemberRequest(
    val email: String,
    val role: MemberRole
)

data class UpdateMemberRoleRequest(
    val role: MemberRole
)

data class MemberResponse(
    val userId: UUID,
    val email: String,
    val name: String,
    val role: MemberRole,
    val joinedAt: Instant
)

// Audit Package DTOs
data class AuditManifestEntry(
    val path: String,
    val sha256: String,
    val sizeBytes: Long
)

data class AuditManifest(
    val generatedAt: String,
    val trailId: String,
    val files: List<AuditManifestEntry>,
    /** HMAC-SHA256 of the JSON-serialised [files] list, keyed with the server-side secret. */
    val hmacSha256: String
)

// Service Account DTOs
data class CreateServiceAccountRequest(
    val name: String,
    val description: String? = null
)

data class UpdateServiceAccountRequest(
    val name: String? = null,
    val description: String? = null
)

data class ServiceAccountResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// API Key DTOs
data class CreateApiKeyRequest(
    /** UUID of the owner — a User or ServiceAccount depending on [ownerType]. */
    val ownerId: UUID,
    /** Human-readable label for this key. */
    val label: String,
    val ownerType: OwnerType,
    /** Optional TTL in days. Null means the key never expires. */
    val ttlDays: Int? = null
)

data class ApiKeyResponse(
    val id: UUID,
    val ownerId: UUID,
    val ownerType: OwnerType,
    val label: String,
    /** First 12 characters of the key (safe to display for identification). */
    val keyPrefix: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val ttlDays: Int?,
    val expiresAt: Instant?
)

/**
 * Returned only once at creation time; contains the plain-text key that must be
 * stored securely by the caller — it cannot be retrieved again.
 */
data class ApiKeyCreatedResponse(
    val id: UUID,
    val ownerId: UUID,
    val ownerType: OwnerType,
    val label: String,
    val keyPrefix: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val ttlDays: Int?,
    val expiresAt: Instant?,
    /** The full plain-text key. Shown exactly once; never persisted in clear text. */
    val plainTextKey: String
)

// Audit Log DTOs
data class AuditEventResponse(
    val id: UUID,
    val eventType: AuditEventType,
    val environmentId: UUID?,
    val trailId: UUID?,
    val artifactSha256: String?,
    val actor: String,
    val payload: String,
    val occurredAt: Instant
)

data class AuditEventPage(
    val events: List<AuditEventResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

// Notification Rule DTOs
data class CreateNotificationRuleRequest(
    val name: String,
    val triggerEvent: TriggerEvent,
    val channelType: ChannelType,
    val channelConfig: String = "{}",
    val filterFlowId: UUID? = null,
    val filterEnvironmentId: UUID? = null
)

data class UpdateNotificationRuleRequest(
    val name: String? = null,
    val isActive: Boolean? = null,
    val triggerEvent: TriggerEvent? = null,
    val channelType: ChannelType? = null,
    val channelConfig: String? = null,
    val filterFlowId: UUID? = null,
    val filterEnvironmentId: UUID? = null,
    /** When true, clears the filterFlowId regardless of the filterFlowId field value. */
    val clearFilterFlowId: Boolean = false,
    /** When true, clears the filterEnvironmentId regardless of the filterEnvironmentId field value. */
    val clearFilterEnvironmentId: Boolean = false
)

data class NotificationRuleResponse(
    val id: UUID,
    val name: String,
    val isActive: Boolean,
    val triggerEvent: TriggerEvent,
    val channelType: ChannelType,
    val channelConfig: String,
    val filterFlowId: UUID?,
    val filterEnvironmentId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Notification Delivery DTOs
data class NotificationDeliveryResponse(
    val id: UUID,
    val ruleId: UUID,
    val eventType: String,
    val payload: String?,
    val status: NotificationDeliveryStatus,
    val sentAt: Instant,
    val error: String?,
    val attemptCount: Int
)

// In-app Notification DTOs
data class NotificationResponse(
    val id: UUID,
    val title: String,
    val message: String,
    val severity: NotificationSeverity,
    val isRead: Boolean,
    val entityType: String?,
    val entityId: UUID?,
    val createdAt: Instant
)

data class NotificationEvent(
    val triggerEvent: TriggerEvent,
    val title: String,
    val message: String,
    val severity: NotificationSeverity = NotificationSeverity.INFO,
    val entityType: String? = null,
    val entityId: UUID? = null,
    val filterFlowId: UUID? = null,
    val filterEnvironmentId: UUID? = null,
    val extraPayload: Map<String, Any?> = emptyMap()
)

// Evidence Collection DTOs
data class ReportCoverageRequest(
    val tool: String,
    val lineCoverage: Double? = null,
    val branchCoverage: Double? = null,
    val minCoverage: Double? = null,
    val reportFileName: String? = null,
    val details: String? = null
)

data class CoverageReportResponse(
    val id: UUID,
    val trailId: UUID,
    val tool: String,
    val lineCoverage: Double?,
    val branchCoverage: Double?,
    val minCoverage: Double?,
    val passed: Boolean,
    val reportFileName: String?,
    val reportFileHash: String?,
    val details: String?,
    val createdAt: Instant
)

data class BulkEvidenceItem(
    val trailId: UUID,
    /** Evidence type (e.g. test-coverage, security-scan, build-provenance, code-review, dependency-audit, license-compliance, container-scan) */
    val evidenceType: String,
    val tool: String,
    val passed: Boolean,
    val details: String? = null
)

data class BulkEvidenceRequest(
    val items: List<BulkEvidenceItem>
)

data class BulkEvidenceResult(
    val trailId: UUID,
    val evidenceType: String,
    val attestationId: UUID,
    val passed: Boolean
)

data class BulkEvidenceResponse(
    val results: List<BulkEvidenceResult>,
    val accepted: Int,
    val failed: Int
)

data class EvidenceSummaryResponse(
    val trailId: UUID,
    val collectedTypes: List<String>,
    val coverageReports: List<CoverageReportResponse>,
    val totalAttestations: Int,
    val passedAttestations: Int,
    val failedAttestations: Int,
    val pendingAttestations: Int,
    val isComplete: Boolean,
    val missingRequiredTypes: List<String>
)

data class EvidenceGapItem(
    val trailId: UUID,
    val gitCommitSha: String,
    val gitBranch: String,
    val flowId: UUID,
    val missingTypes: List<String>,
    val trailStatus: TrailStatus
)

data class EvidenceGapsResponse(
    val gaps: List<EvidenceGapItem>,
    val totalTrailsWithGaps: Int
)

// SSO Configuration DTOs
data class CreateSsoConfigRequest(
    val provider: SsoProvider,
    val issuerUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val attributeMappings: String = """{"email":"email","name":"name"}""",
    val groupRoleMappings: String = "{}",
    val isMandatory: Boolean = false
)

data class UpdateSsoConfigRequest(
    val provider: SsoProvider? = null,
    val issuerUrl: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val attributeMappings: String? = null,
    val groupRoleMappings: String? = null,
    val isMandatory: Boolean? = null
)

data class SsoConfigResponse(
    val id: UUID,
    val orgSlug: String,
    val provider: SsoProvider,
    val issuerUrl: String,
    val clientId: String,
    // clientSecret is intentionally omitted — it is never returned in API responses.
    val attributeMappings: String,
    val groupRoleMappings: String,
    val isMandatory: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Vault Evidence DTOs
data class StoreEvidenceRequest(
    val evidenceType: String,
    val data: Map<String, String>
)

data class VaultEvidenceResponse(
    val entityType: String,
    val entityId: String,
    val evidenceType: String,
    val vaultPath: String,
    val version: Int,
    val data: Map<String, String>? = null,
    val storedAt: Instant
)

data class VaultEvidenceListResponse(
    val entityType: String,
    val entityId: String,
    val evidenceTypes: List<String>
)

data class VaultHealthResponse(
    val healthy: Boolean,
    val vaultUri: String,
    val authMethod: String,
    val message: String,
    val checkedAt: Instant = Instant.now()
)

// Policy DTOs
data class CreatePolicyRequest(
    val name: String,
    val enforceProvenance: Boolean = false,
    val enforceTrailCompliance: Boolean = false,
    val requiredAttestationTypes: List<String> = emptyList(),
    val orgSlug: String? = null
)

data class UpdatePolicyRequest(
    val name: String? = null,
    val enforceProvenance: Boolean? = null,
    val enforceTrailCompliance: Boolean? = null,
    val requiredAttestationTypes: List<String>? = null
)

data class PolicyResponse(
    val id: UUID,
    val name: String,
    val enforceProvenance: Boolean,
    val enforceTrailCompliance: Boolean,
    val requiredAttestationTypes: List<String>,
    val orgSlug: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SsoTestConnectionResponse(
    val success: Boolean,
    val message: String,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null
)

data class SsoLoginUrlResponse(
    val loginUrl: String,
    val state: String
)

data class SsoCallbackResponse(
    val token: String,
    val userId: UUID,
    val email: String,
    val name: String
)

// PolicyAttachment DTOs
data class CreatePolicyAttachmentRequest(
    val policyId: UUID,
    val environmentId: UUID
)

data class PolicyAttachmentResponse(
    val id: UUID,
    val policyId: UUID,
    val environmentId: UUID,
    val createdAt: Instant
)

// Logical Environment DTOs
// LogicalEnvironment DTOs
data class CreateLogicalEnvironmentRequest(
    val name: String,
    val description: String = ""
)

data class UpdateLogicalEnvironmentRequest(
    val name: String? = null,
    val description: String? = null
)

data class LogicalEnvironmentMemberResponse(
    val physicalEnvId: UUID,
    val physicalEnvName: String,
    val physicalEnvType: EnvironmentType,
    val addedAt: Instant
)

data class LogicalEnvironmentResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val members: List<LogicalEnvironmentMemberResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Environment Allowlist DTOs
data class CreateAllowlistEntryRequest(
    val sha256: String? = null,
    val namePattern: String? = null,
    val reason: String,
    val approvedBy: String,
    val expiresAt: Instant? = null
)

data class AllowlistEntryResponse(
    val id: UUID,
    val environmentId: UUID,
    val sha256: String?,
    val namePattern: String?,
    val reason: String,
    val approvedBy: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val status: AllowlistEntryStatus,
    val isEffective: Boolean
)

// Organisation DTOs
data class CreateOrganisationRequest(
    val slug: String,
    val name: String,
    val description: String = "",
    val type: OrgType = OrgType.SHARED
)

data class UpdateOrganisationRequest(
    val name: String? = null,
    val description: String? = null
)

// OPA Policy DTOs
data class UploadBundleRequest(
    val name: String,
    val version: String,
    val regoContent: String,
    val orgSlug: String? = null
)

data class BundleResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val regoContent: String,
    val status: BundleStatus,
    val orgSlug: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class EvaluatePolicyRequest(
    val artifactName: String,
    val artifactVersion: String? = null,
    val environment: String? = null,
    val attestations: List<Map<String, String>> = emptyList(),
    val approvalStatus: String? = null
)

data class PolicyDecisionResponse(
    val id: UUID,
    val bundleId: UUID?,
    val inputJson: String,
    val resultAllow: Boolean,
    val denyReasons: List<String>,
    val orgSlug: String?,
    val evaluatedAt: Instant
)

data class OrganisationResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val description: String,
    val type: OrgType,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Approval DTOs
data class CreateApprovalRequest(
    val trailId: UUID,
    val requiredApprovers: List<String> = emptyList(),
    val comments: String? = null,
    val deadline: Instant? = null
)

data class ApproveRequest(
    val approverIdentity: String,
    val comments: String? = null
)

data class RejectRequest(
    val approverIdentity: String,
    val comments: String? = null
)

data class ApprovalDecisionResponse(
    val id: UUID,
    val approvalId: UUID,
    val approverIdentity: String,
    val decision: ApprovalDecisionType,
    val comments: String?,
    val decidedAt: Instant
)

data class ApprovalResponse(
    val id: UUID,
    val trailId: UUID,
    val flowId: UUID,
    val status: ApprovalStatus,
    val requiredApprovers: List<String>,
    val comments: String?,
    val requestedAt: Instant,
    val deadline: Instant?,
    val resolvedAt: Instant?,
    val decisions: List<ApprovalDecisionResponse> = emptyList()
)

// Deployment Policy DTOs
data class CreateDeploymentPolicyRequest(
    val name: String,
    val description: String = "",
    val flowId: UUID,
    val environmentId: UUID? = null,
    val enforceProvenance: Boolean = false,
    val enforceApprovals: Boolean = false,
    val requiredAttestationTypes: List<String> = emptyList()
)

data class UpdateDeploymentPolicyRequest(
    val name: String? = null,
    val description: String? = null,
    val enforceProvenance: Boolean? = null,
    val enforceApprovals: Boolean? = null,
    val requiredAttestationTypes: List<String>? = null,
    val isActive: Boolean? = null
)

data class DeploymentPolicyResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val flowId: UUID,
    val environmentId: UUID?,
    val enforceProvenance: Boolean,
    val enforceApprovals: Boolean,
    val requiredAttestationTypes: List<String>,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Gate DTOs
data class GateEvaluateRequest(
    val artifactSha256: String,
    val environmentId: UUID? = null,
    val requestedBy: String? = null
)

data class GateEvaluateResponse(
    val id: UUID,
    val decision: GateDecision,
    val artifactSha256: String,
    val environmentId: UUID?,
    val evaluatedAt: Instant,
    val blockReasons: List<String>,
    val policiesEvaluated: Int
)

// Pull Request Attestation DTOs
data class CreatePrAttestationRequest(
    val provider: ScmProvider,
    val repository: String,
    val commitSha: String,
    val assertOnMissing: Boolean = false,
    val orgSlug: String? = null
)

// SCM Integration DTOs
data class CreateScmIntegrationRequest(
    val provider: ScmProvider,
    val token: String
)

data class ScmIntegrationResponse(
    val id: UUID,
    val orgSlug: String,
    val provider: ScmProvider,
    val createdAt: Instant
)

// Security Scan DTOs
data class RecordSecurityScanRequest(
    val tool: String,
    val toolVersion: String? = null,
    val scanType: ScanType? = null,
    val target: String? = null,
    val criticalVulnerabilities: Int = 0,
    val highVulnerabilities: Int = 0,
    val mediumVulnerabilities: Int = 0,
    val lowVulnerabilities: Int = 0,
    val informational: Int = 0,
    val scanDurationSeconds: Long? = null,
    val reportUrl: String? = null,
    val orgSlug: String? = null
)

data class SecurityScanResponse(
    val id: UUID,
    val trailId: UUID,
    val attestationId: UUID?,
    val tool: String,
    val toolVersion: String?,
    val scanType: ScanType?,
    val target: String?,
    val criticalVulnerabilities: Int,
    val highVulnerabilities: Int,
    val mediumVulnerabilities: Int,
    val lowVulnerabilities: Int,
    val informational: Int,
    val scanDurationSeconds: Long?,
    val reportUrl: String?,
    val orgSlug: String?,
    val createdAt: Instant,
    val thresholdBreached: Boolean = false,
    val breachDetails: List<String> = emptyList()
)

data class SecurityScanSummaryResponse(
    val totalScans: Int,
    val totalCritical: Int,
    val totalHigh: Int,
    val totalMedium: Int,
    val totalLow: Int,
    val scansWithCritical: Int
)

data class SetSecurityThresholdRequest(
    val maxCritical: Int = 0,
    val maxHigh: Int = 0,
    val maxMedium: Int = 10,
    val maxLow: Int = Int.MAX_VALUE
)

data class SecurityThresholdResponse(
    val id: UUID,
    val flowId: UUID,
    val maxCritical: Int,
    val maxHigh: Int,
    val maxMedium: Int,
    val maxLow: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ThresholdEvaluationResult(
    val passed: Boolean,
    val breaches: List<String>
)

// Regulatory Compliance DTOs
data class CreateFrameworkRequest(
    val name: String,
    val version: String,
    val description: String? = null,
    val orgSlug: String? = null
)

data class FrameworkResponse(
    val id: UUID,
    val name: String,
    val version: String,
    val description: String?,
    val isActive: Boolean,
    val orgSlug: String?,
    val controls: List<ControlResponse> = emptyList(),
    val createdAt: Instant
)

data class CreateControlRequest(
    val controlId: String,
    val title: String,
    val description: String? = null,
    val requiredEvidenceTypes: List<String> = emptyList()
)

data class ControlResponse(
    val id: UUID,
    val frameworkId: UUID,
    val controlId: String,
    val title: String,
    val description: String?,
    val requiredEvidenceTypes: List<String>,
    val createdAt: Instant
)

data class CreateMappingRequest(
    val regulatoryControlId: UUID,
    val flowId: UUID,
    val attestationType: String,
    val isMandatory: Boolean = true
)

data class MappingResponse(
    val id: UUID,
    val regulatoryControlId: UUID,
    val flowId: UUID,
    val attestationType: String,
    val isMandatory: Boolean,
    val createdAt: Instant
)

data class AssessTrailRequest(
    val frameworkId: UUID,
    val trailId: UUID,
    val orgSlug: String? = null
)

data class ControlResult(
    val controlId: String,
    val title: String,
    val status: String,
    val satisfiedBy: List<String> = emptyList(),
    val missingEvidence: List<String> = emptyList()
)

data class AssessmentResponse(
    val id: UUID,
    val frameworkId: UUID,
    val trailId: UUID,
    val overallStatus: AssessmentStatus,
    val controlResults: List<ControlResult>,
    val orgSlug: String?,
    val assessedAt: Instant
)

data class RegulatoryReportResponse(
    val frameworkId: UUID,
    val frameworkName: String,
    val frameworkVersion: String,
    val assessments: List<AssessmentResponse>,
    val generatedAt: Instant
)

// Metrics DTOs
data class ComplianceMetricsSummary(
    val totalTrails: Int,
    val compliantTrails: Int,
    val nonCompliantTrails: Int,
    val complianceRate: Double,
    val totalAttestations: Int,
    val passedAttestations: Int,
    val failedAttestations: Int,
    val generatedAt: Instant
)

data class SecurityMetricsSummary(
    val totalScans: Int,
    val passedScans: Int,
    val failedScans: Int,
    val totalCritical: Int,
    val totalHigh: Int,
    val totalMedium: Int,
    val totalLow: Int,
    val generatedAt: Instant
)

data class MemberSnapshotSummary(
    val physicalEnvId: UUID,
    val physicalEnvName: String,
    val snapshotIndex: Long?,
    val recordedAt: Instant?,
    val artifactCount: Int
)

data class MergedSnapshotArtifact(
    val artifactSha256: String,
    val artifactName: String?,
    val artifactTag: String?,
    val instanceCount: Int?,
    val physicalEnvId: UUID,
    val physicalEnvName: String
)

data class MergedSnapshotResponse(
    val logicalEnvId: UUID,
    val logicalEnvName: String,
    val complianceStatus: ComplianceStatus,
    val memberSnapshots: List<MemberSnapshotSummary>,
    val mergedArtifacts: List<MergedSnapshotArtifact>
)
