package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class NotificationSeverity { INFO, WARNING, CRITICAL }

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var message: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var severity: NotificationSeverity = NotificationSeverity.INFO,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "entity_type")
    var entityType: String? = null,

    @Column(name = "entity_id")
    var entityId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
