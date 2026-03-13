package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class NotificationDeliveryStatus { SENT, FAILED, SKIPPED }

@Entity
@Table(name = "notification_deliveries")
class NotificationDelivery(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "rule_id", nullable = false)
    var ruleId: UUID,

    @Column(name = "event_type", nullable = false)
    var eventType: String,

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NotificationDeliveryStatus,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),

    @Column(columnDefinition = "TEXT")
    var error: String? = null,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 1
)
