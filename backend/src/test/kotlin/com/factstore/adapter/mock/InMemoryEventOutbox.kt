package com.factstore.adapter.mock

import com.factstore.core.port.outbound.IEventOutbox
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryEventOutbox : IEventOutbox {
    private val entries = ConcurrentHashMap<Long, Instant>()
    val failures = ConcurrentHashMap<Long, String>()

    override fun enqueue(sequenceNumber: Long) { entries[sequenceNumber] = Instant.now() }
    override fun remove(sequenceNumber: Long) { entries.remove(sequenceNumber) }
    override fun recordFailure(sequenceNumber: Long, error: String) { failures[sequenceNumber] = error }
    override fun pending(enqueuedBefore: Instant, limit: Int): List<Long> =
        entries.filterValues { it.isBefore(enqueuedBefore) }.keys.sorted().take(limit)
}
