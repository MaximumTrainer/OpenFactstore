package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class WebhookSource { GITHUB, JENKINS, CIRCLECI, GITLAB, GENERIC }

@Entity
@Table(name = "webhook_configs")
class WebhookConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: WebhookSource,

    @Column(name = "secret_hash", nullable = false)
    var secretHash: String,

    @Column(name = "flow_id", nullable = false)
    var flowId: UUID,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
