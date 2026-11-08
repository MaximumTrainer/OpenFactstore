package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A committed domain event that has not yet been confirmed as published to the event bus (#185).
 *
 * Written in the same transaction as the [EventLogEntry] it refers to, and removed once the
 * publish succeeds. Whatever is left is a fact the query side has not heard about, and the relay
 * re-publishes it. The event log itself stays append-only: publication state lives here instead.
 */
@Entity
@Table(name = "event_outbox")
class EventOutboxEntry(
    @Id
    @Column(name = "sequence_number")
    val sequenceNumber: Long,

    @Column(name = "enqueued_at", nullable = false)
    val enqueuedAt: Instant = Instant.now(),

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null
)
