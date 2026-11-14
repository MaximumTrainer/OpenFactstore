package com.factstore.dto.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * CQRS Command DTOs — used exclusively on the write path.
 * Each command represents an intent to change state.
 *
 * Request DTOs (suffixed with `Request`) are deserialized from the HTTP body and
 * intentionally omit fields supplied by path variables. Controllers combine the
 * request body with path parameters to build the full Command.
 */

// ── Flow Commands ──────────────────────────────────────────────────────────────

data class CreateFlowCommand(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 255)
    val name: String,
    @field:Size(max = 2048)
    val description: String = "",
    val requiredAttestationTypes: List<String> = emptyList(),
    val tags: Map<String, String> = emptyMap(),
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean = false,
    val requiredApproverRoles: List<String> = emptyList(),
    /** Apply a hub template at creation; its YAML is copied onto the flow (#162, #250). */
    val templateId: String? = null,
    /** Apply several hub templates combined. */
    val templateIds: List<String>? = null,
    /** #240: see `Flow.rejectSelfAsserted`. Optional, default false. */
    val rejectSelfAsserted: Boolean? = null,
    val acceptedSelfAssertedTypes: List<String>? = null
)

/** JSON body for PUT /api/v2/flows/{id} — `id` comes from the path variable. */
data class UpdateFlowRequest(
    val name: String? = null,
    val description: String? = null,
    val requiredAttestationTypes: List<String>? = null,
    val tags: Map<String, String>? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean? = null,
    val requiredApproverRoles: List<String>? = null,
    val rejectSelfAsserted: Boolean? = null,
    val acceptedSelfAssertedTypes: List<String>? = null
)

data class UpdateFlowCommand(
    val id: UUID,
    val name: String? = null,
    val description: String? = null,
    val requiredAttestationTypes: List<String>? = null,
    val tags: Map<String, String>? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean? = null,
    val requiredApproverRoles: List<String>? = null,
    val rejectSelfAsserted: Boolean? = null,
    val acceptedSelfAssertedTypes: List<String>? = null
)

data class DeleteFlowCommand(
    val id: UUID,
    /** Deleting a flow orphans the evidence recorded against it, so trails block it unless forced (#161). */
    val force: Boolean = false
)

// ── Trail Commands ─────────────────────────────────────────────────────────────

data class CreateTrailCommand(
    val flowId: UUID,
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    @field:NotBlank(message = "gitAuthor is required")
    val gitAuthor: String,
    /** The author email as git reports it; the same wide rule as v1 (bot addresses are allowed). */
    @field:Pattern(
        regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@.]+$",
        message = "gitAuthorEmail must be an address of the form local@domain.tld"
    )
    @field:Size(max = 255)
    val gitAuthorEmail: String,
    val pullRequestId: String? = null,
    val pullRequestReviewer: String? = null,
    val deploymentActor: String? = null,
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val buildUrl: String? = null,
    val name: String? = null,
    /** Stable release identifier; makes trail creation idempotent per flow (#164). */
    val externalId: String? = null,
    val tags: Map<String, String>? = null
)

// ── Artifact Commands ──────────────────────────────────────────────────────────

/** JSON body for POST /api/v2/trails/{trailId}/artifacts — `trailId` comes from the path. */
data class ReportArtifactRequest(
    @field:NotBlank(message = "imageName is required")
    val imageName: String,
    @field:NotBlank(message = "imageTag is required")
    val imageTag: String,
    @field:NotBlank(message = "sha256Digest is required")
    @field:Pattern(regexp = "sha256:[a-fA-F0-9]{64}", message = "sha256Digest must be in format sha256:<64 hex chars>")
    val sha256Digest: String,
    val registry: String? = null,
    val reportedBy: String,
    val orgSlug: String? = null,
    val tags: Map<String, String>? = null
)

data class ReportArtifactCommand(
    val trailId: UUID,
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String? = null,
    val reportedBy: String,
    val orgSlug: String? = null,
    val tags: Map<String, String>? = null
)

// ── Attestation Commands ───────────────────────────────────────────────────────

/** JSON body for POST /api/v2/trails/{trailId}/attestations — `trailId` comes from the path. */
data class RecordAttestationRequest(
    val type: String,
    val status: com.factstore.core.domain.AttestationStatus = com.factstore.core.domain.AttestationStatus.PENDING,
    val details: String? = null,
    val name: String? = null,
    val evidenceUrl: String? = null,
    val orgSlug: String? = null,
    val artifactFingerprint: String? = null,
    val flowName: String? = null,
    /** Structured result data, checked against the type's schema when it is a custom type. */
    val attestationData: String? = null,
    /** Links to the evidence; defaults to `evidenceUrl` when empty. */
    val externalUrls: List<String>? = null,
    val annotations: Map<String, String>? = null,
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    val gitRepoUrl: String? = null
)

data class RecordAttestationCommand(
    val trailId: UUID,
    val type: String,
    val status: com.factstore.core.domain.AttestationStatus = com.factstore.core.domain.AttestationStatus.PENDING,
    val details: String? = null,
    val name: String? = null,
    val evidenceUrl: String? = null,
    val orgSlug: String? = null,
    val artifactFingerprint: String? = null,
    val flowName: String? = null,
    /** Structured result data, checked against the type's schema when it is a custom type. */
    val attestationData: String? = null,
    /** Links to the evidence; defaults to `evidenceUrl` when empty. */
    val externalUrls: List<String>? = null,
    val annotations: Map<String, String>? = null,
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    val gitRepoUrl: String? = null
)

data class UploadEvidenceCommand(
    val trailId: UUID,
    val attestationId: UUID,
    val fileName: String,
    val contentType: String,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UploadEvidenceCommand) return false
        return trailId == other.trailId && attestationId == other.attestationId && fileName == other.fileName
    }

    override fun hashCode(): Int = listOf(trailId, attestationId, fileName).hashCode()
}

// ── Command Result ─────────────────────────────────────────────────────────────

data class CommandResult(
    val id: UUID,
    val status: String = "created",
    val timestamp: java.time.Instant = java.time.Instant.now()
)
