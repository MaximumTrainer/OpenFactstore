package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class TriggerEvent {
    ATTESTATION_FAILED,
    GATE_BLOCKED,
    DRIFT_DETECTED,
    APPROVAL_REQUIRED,
    TRAIL_NON_COMPLIANT,
    APPROVAL_REJECTED
}

enum class ChannelType { SLACK, WEBHOOK, IN_APP }

@Entity
@Table(name = "notification_rules")
class NotificationRule(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false)
    var triggerEvent: TriggerEvent,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    var channelType: ChannelType,

    @Column(name = "channel_config", columnDefinition = "TEXT", nullable = false)
    var channelConfig: String = "{}",

    @Column(name = "filter_flow_id")
    var filterFlowId: UUID? = null,

    @Column(name = "filter_environment_id")
    var filterEnvironmentId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
