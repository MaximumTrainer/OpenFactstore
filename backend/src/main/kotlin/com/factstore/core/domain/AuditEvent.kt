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
    GATE_ALLOWED,
    // A gate could not be evaluated and fell back by its environment's risk tier (#193).
    GATE_FALLBACK,
    // Flow definition changes are themselves compliance-relevant: they change what an
    // existing trail will be judged against on its next assert (#160).
    FLOW_UPDATED,
    FLOW_ARCHIVED,
    FLOW_UNARCHIVED,
    FLOW_RENAMED,
    FLOW_DELETED,
    // Authentication and session events (#156 FR-1.5, FR-7.4). Sign-in is an event in its
    // own right: "who was in the system, when" is a question a compliance product must answer.
    USER_SIGNED_IN,
    USER_SIGNED_OUT,
    USER_SIGN_IN_FAILED,
    USER_SESSIONS_REVOKED,
    USER_ROLE_CHANGED,
    // Authorisation outcomes (#155 FR-8.1).
    AUTH_FAILED,
    // An unbound credential read another organisation's resources (#207, #155 FR-5.4).
    CROSS_TENANT_READ,
    // A fact schema version was published to the registry (#195).
    SCHEMA_PUBLISHED,
    // The trusted registry of source signing keys (#187).
    SIGNING_KEY_REGISTERED,
    SIGNING_KEY_REVOKED,
    // Cryptographic shredding of a tenant-month data key (#200): requested, then approved by a second admin.
    DATA_KEY_SHRED_REQUESTED,
    DATA_KEY_SHREDDED,
    AUTH_DENIED,
    // Credential lifecycle (#155 FR-8.2).
    API_KEY_CREATED,
    API_KEY_ROTATED,
    API_KEY_REVOKED,
    // Removing evidence is itself compliance-relevant, so the removal goes on the record (#161).
    TRAIL_ARCHIVED,
    TRAIL_UNARCHIVED,
    TRAIL_DELETED
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
    val occurredAt: Instant = Instant.now(),

    @Column(nullable = true, length = 100)
    var region: String? = null
)
