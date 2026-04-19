package com.factstore.dto.query

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.domain.TrailStatus
import java.time.Instant
import java.util.UUID

/**
 * CQRS Read Models (Views/Projections) — used exclusively on the read path.
 * Optimised for high-performance retrieval without heavy business logic.
 */

// ── Flow Views ─────────────────────────────────────────────────────────────────

data class FlowView(
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

data class FlowTemplateView(
    val flowId: UUID,
    val templateYaml: String?,
    val effectiveTemplate: Map<String, Any>?
)

// ── Trail Views ────────────────────────────────────────────────────────────────

data class TrailView(
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

// ── Artifact Views ─────────────────────────────────────────────────────────────

data class ArtifactView(
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

// ── Attestation Views ──────────────────────────────────────────────────────────

data class AttestationView(
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

data class EvidenceFileView(
    val id: UUID,
    val attestationId: UUID,
    val fileName: String,
    val sha256Hash: String,
    val fileSizeBytes: Long,
    val contentType: String,
    val storedAt: Instant,
    val externalUrl: String? = null
)
