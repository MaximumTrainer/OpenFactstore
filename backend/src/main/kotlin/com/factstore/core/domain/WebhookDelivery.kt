package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class DeliveryStatus { SUCCESS, FAILED, DUPLICATE }

@Entity
@Table(name = "webhook_deliveries")
class WebhookDelivery(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "webhook_config_id", nullable = false)
    var webhookConfigId: UUID,

    @Column(name = "delivery_id", nullable = false)
    var deliveryId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: WebhookSource,

    @Column(name = "event_type")
    var eventType: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeliveryStatus,

    @Column(name = "status_message", columnDefinition = "TEXT")
    var statusMessage: String? = null,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now()
)
