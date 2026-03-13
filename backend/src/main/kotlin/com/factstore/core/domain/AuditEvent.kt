package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AuditEventType {
    ARTIFACT_DEPLOYED,
    ARTIFACT_REMOVED,
    ARTIFACT_UPDATED,
    ENVIRONMENT_CREATED,
    ENVIRONMENT_DELETED,
    POLICY_EVALUATED,
    ATTESTATION_RECORDED,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    GATE_BLOCKED,
    GATE_ALLOWED
}

@Entity
@Table(name = "audit_events")
class AuditEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: AuditEventType,

    @Column(name = "environment_id")
    val environmentId: UUID? = null,

    @Column(name = "trail_id")
    val trailId: UUID? = null,

    @Column(name = "artifact_sha256")
    val artifactSha256: String? = null,

    @Column(nullable = false)
    val actor: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now()
)
