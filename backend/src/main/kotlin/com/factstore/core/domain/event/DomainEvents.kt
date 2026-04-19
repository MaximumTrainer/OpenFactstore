package com.factstore.core.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Sealed hierarchy of domain events emitted by command handlers.
 * Each event represents an immutable fact that has occurred in the system.
 */
sealed class DomainEvent {
    abstract val eventId: UUID
    abstract val aggregateId: UUID
    abstract val aggregateType: String
    abstract val occurredAt: Instant

    // ── Flow Events ────────────────────────────────────────────────────────────

    data class FlowCreated(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val name: String,
        val description: String,
        val orgSlug: String? = null,
        val requiredAttestationTypes: List<String> = emptyList(),
        val tags: Map<String, String> = emptyMap(),
        val templateYaml: String? = null,
        val requiresApproval: Boolean = false,
        val requiredApproverRoles: List<String> = emptyList()
    ) : DomainEvent() {
        override val aggregateType: String = "Flow"
    }

    data class FlowUpdated(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val name: String? = null,
        val description: String? = null,
        val requiredAttestationTypes: List<String>? = null,
        val tags: Map<String, String>? = null,
        val templateYaml: String? = null,
        val requiresApproval: Boolean? = null,
        val requiredApproverRoles: List<String>? = null
    ) : DomainEvent() {
        override val aggregateType: String = "Flow"
    }

    data class FlowDeleted(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now()
    ) : DomainEvent() {
        override val aggregateType: String = "Flow"
    }

    // ── Trail Events ───────────────────────────────────────────────────────────

    data class TrailCreated(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val flowId: UUID,
        val gitCommitSha: String,
        val gitBranch: String,
        val gitAuthor: String,
        val gitAuthorEmail: String,
        val pullRequestId: String? = null,
        val pullRequestReviewer: String? = null,
        val deploymentActor: String? = null,
        val orgSlug: String? = null,
        val templateYaml: String? = null,
        val buildUrl: String? = null
    ) : DomainEvent() {
        override val aggregateType: String = "Trail"
    }

    // ── Artifact Events ────────────────────────────────────────────────────────

    data class ArtifactReported(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val trailId: UUID,
        val imageName: String,
        val imageTag: String,
        val sha256Digest: String,
        val registry: String? = null,
        val reportedBy: String,
        val orgSlug: String? = null
    ) : DomainEvent() {
        override val aggregateType: String = "Artifact"
    }

    // ── Attestation Events ─────────────────────────────────────────────────────

    data class AttestationRecorded(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val trailId: UUID,
        val type: String,
        val status: String,
        val details: String? = null,
        val name: String? = null,
        val evidenceUrl: String? = null,
        val orgSlug: String? = null,
        val artifactFingerprint: String? = null,
        val flowName: String? = null
    ) : DomainEvent() {
        override val aggregateType: String = "Attestation"
    }

    data class EvidenceUploaded(
        override val eventId: UUID = UUID.randomUUID(),
        override val aggregateId: UUID,
        override val occurredAt: Instant = Instant.now(),
        val trailId: UUID,
        val fileName: String,
        val contentType: String,
        val sha256Hash: String,
        val fileSizeBytes: Long
    ) : DomainEvent() {
        override val aggregateType: String = "Attestation"
    }
}
