package com.factstore.adapter.mock

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IEventStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory implementation of [IEventStore] for use in unit tests.
 */
class InMemoryEventStore : IEventStore {
    private val log = mutableListOf<EventLogEntry>()
    private val seq = AtomicLong(0)

    override fun append(entry: EventLogEntry): EventLogEntry {
        val stored = EventLogEntry(
            sequenceNumber = seq.incrementAndGet(),
            eventId = entry.eventId,
            aggregateId = entry.aggregateId,
            aggregateType = entry.aggregateType,
            eventType = entry.eventType,
            payload = entry.payload,
            metadata = entry.metadata,
            occurredAt = entry.occurredAt
        )
        log.add(stored)
        return stored
    }

    override fun findByAggregateId(aggregateId: UUID): List<EventLogEntry> =
        log.filter { it.aggregateId == aggregateId }.sortedBy { it.sequenceNumber }

    override fun findByAggregateType(aggregateType: String): List<EventLogEntry> =
        log.filter { it.aggregateType == aggregateType }.sortedBy { it.sequenceNumber }

    override fun findAll(): List<EventLogEntry> = log.sortedBy { it.sequenceNumber }

    override fun findAfterSequence(afterSequence: Long): List<EventLogEntry> =
        log.filter { it.sequenceNumber > afterSequence }.sortedBy { it.sequenceNumber }

    /** Helper for tests to inspect events. */
    fun events(): List<EventLogEntry> = log.toList()

    fun clear() {
        log.clear()
        seq.set(0)
    }
}
