package com.factstore.core.port.outbound

import java.time.Instant
import java.util.UUID

sealed class SupplyChainEvent {
    abstract val id: UUID
    abstract val occurredAt: Instant
    abstract val orgSlug: String?

    data class AttestationRecorded(
        override val id: UUID = UUID.randomUUID(),
        override val occurredAt: Instant = Instant.now(),
        override val orgSlug: String? = null,
        val trailId: UUID,
        val attestationType: String,
        val artifactFingerprint: String? = null
    ) : SupplyChainEvent()

    data class GateEvaluated(
        override val id: UUID = UUID.randomUUID(),
        override val occurredAt: Instant = Instant.now(),
        override val orgSlug: String? = null,
        val artifactSha256: String,
        val environment: String?,
        val allowed: Boolean,
        val policyName: String? = null
    ) : SupplyChainEvent()

    data class ApprovalDecided(
        override val id: UUID = UUID.randomUUID(),
        override val occurredAt: Instant = Instant.now(),
        override val orgSlug: String? = null,
        val approvalId: UUID,
        val decision: String,
        val deciderUserId: UUID? = null
    ) : SupplyChainEvent()

    data class DriftDetected(
        override val id: UUID = UUID.randomUUID(),
        override val occurredAt: Instant = Instant.now(),
        override val orgSlug: String? = null,
        val environmentId: UUID,
        val environmentName: String,
        val addedArtifacts: Int,
        val removedArtifacts: Int
    ) : SupplyChainEvent()

    data class SnapshotTaken(
        override val id: UUID = UUID.randomUUID(),
        override val occurredAt: Instant = Instant.now(),
        override val orgSlug: String? = null,
        val environmentId: UUID,
        val snapshotId: UUID,
        val artifactCount: Int
    ) : SupplyChainEvent()
}

interface IEventPublisher {
    fun publish(event: SupplyChainEvent)
}
