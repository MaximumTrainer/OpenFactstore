package com.factstore.core.port.outbound

import java.time.Instant

/**
 * Outbound port for the transactional outbox that backs post-commit publishing (#185).
 */
interface IEventOutbox {
    /** Record that the event with [sequenceNumber] still has to be published. Joins the caller's transaction. */
    fun enqueue(sequenceNumber: Long)

    /** The event was published; forget it. */
    fun remove(sequenceNumber: Long)

    /** Record a failed publish attempt. */
    fun recordFailure(sequenceNumber: Long, error: String)

    /** Sequence numbers enqueued before [enqueuedBefore], oldest first. */
    fun pending(enqueuedBefore: Instant, limit: Int): List<Long>
}
