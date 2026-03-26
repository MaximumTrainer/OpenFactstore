package com.factstore.dto.command

import java.util.UUID

/**
 * CQRS Command DTOs — used exclusively on the write path.
 * Each command represents an intent to change state.
 */

// ── Flow Commands ──────────────────────────────────────────────────────────────

data class CreateFlowCommand(
    val name: String,
    val description: String = "",
    val requiredAttestationTypes: List<String> = emptyList(),
    val tags: Map<String, String> = emptyMap(),
    val orgSlug: String? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean = false,
    val requiredApproverRoles: List<String> = emptyList()
)

data class UpdateFlowCommand(
    val id: UUID,
    val name: String? = null,
    val description: String? = null,
    val requiredAttestationTypes: List<String>? = null,
    val tags: Map<String, String>? = null,
    val templateYaml: String? = null,
    val requiresApproval: Boolean? = null,
    val requiredApproverRoles: List<String>? = null
)

data class DeleteFlowCommand(val id: UUID)

// ── Trail Commands ─────────────────────────────────────────────────────────────

data class CreateTrailCommand(
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

// ── Artifact Commands ──────────────────────────────────────────────────────────

data class ReportArtifactCommand(
    val trailId: UUID,
    val imageName: String,
    val imageTag: String,
    val sha256Digest: String,
    val registry: String? = null,
    val reportedBy: String,
    val orgSlug: String? = null
)

// ── Attestation Commands ───────────────────────────────────────────────────────

data class RecordAttestationCommand(
    val trailId: UUID,
    val type: String,
    val status: com.factstore.core.domain.AttestationStatus = com.factstore.core.domain.AttestationStatus.PENDING,
    val details: String? = null,
    val name: String? = null,
    val evidenceUrl: String? = null,
    val orgSlug: String? = null,
    val artifactFingerprint: String? = null,
    val flowName: String? = null
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
