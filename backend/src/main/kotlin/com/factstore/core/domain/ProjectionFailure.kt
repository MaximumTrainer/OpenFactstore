package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A domain event the read-model projector could not apply (#185).
 *
 * Previously such a message was acknowledged as if it had succeeded and the read side was left
 * silently stale. It is now kept here, with its payload, so it can be inspected and replayed.
 */
@Entity
@Table(name = "projection_failures")
class ProjectionFailure(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "sequence_number", nullable = false)
    val sequenceNumber: Long,

    @Column(name = "event_type", nullable = false, length = 128)
    val eventType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "reason", nullable = false, length = 1000)
    val reason: String,

    @Column(name = "failed_at", nullable = false)
    val failedAt: Instant = Instant.now()
)
