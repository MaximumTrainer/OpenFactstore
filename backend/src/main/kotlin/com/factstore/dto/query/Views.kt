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
    val requiredApproverRoles: List<String> = emptyList(),
    val archivedAt: Instant? = null,
    /** Hub template(s) the flow was created from, `+`-joined (#162). */
    val templateId: String? = null,
    val templateVersion: String? = null,
    /** #240: whether assert and gates refuse self-asserted attestations, and the exceptions. */
    val rejectSelfAsserted: Boolean = false,
    val acceptedSelfAssertedTypes: List<String> = emptyList()
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
    val name: String? = null,
    val externalId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val tags: Map<String, String> = emptyMap()
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
    val provenanceStatus: ProvenanceStatus = ProvenanceStatus.NO_PROVENANCE,
    val tags: Map<String, String> = emptyMap()
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
    val createdAt: Instant,
    val attestationData: String? = null,
    val externalUrls: List<String> = emptyList(),
    val annotations: Map<String, String> = emptyMap(),
    val gitCommitSha: String? = null,
    val gitBranch: String? = null,
    val gitRepoUrl: String? = null,
    val artifactId: UUID? = null,
    val overridesAttestationId: UUID? = null,
    val justification: String? = null,
    /** #240: true when the status is the caller's claim, not decided by the server. */
    val selfAsserted: Boolean = false
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
