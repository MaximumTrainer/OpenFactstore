package com.factstore.application

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.domain.event.DomainEventRegistry
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Replays events from the Event Store to rebuild read-model state.
 *
 * In the current implementation the read-model tables are the same JPA
 * entities that the command side writes, so a full replay is equivalent to
 * verifying consistency.  Future iterations may project into separate
 * denormalised tables or external stores.
 */
@Service
class EventProjector(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(EventProjector::class.java)

    private val eventTypeMap = DomainEventRegistry.eventTypeMap

    /**
     * Replay every event in the store, invoking [handler] for each
     * successfully deserialized [DomainEvent].  Returns the number of events
     * that were deserialized and handed to [handler]; entries with unknown
     * types or malformed payloads are skipped and not counted.
     */
    fun replayAll(handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findAll()
        var processed = 0L
        entries.forEach { entry -> deserialize(entry)?.let { handler(it); processed++ } }
        log.info("Replayed {}/{} events from the event store", processed, entries.size)
        return processed
    }

    /**
     * Replay events whose sequence number is greater than [afterSequence].
     * Useful for incremental catch-up when the projector is running
     * continuously.  Returns the number of events successfully processed.
     */
    fun replayAfter(afterSequence: Long, handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findAfterSequence(afterSequence)
        var processed = 0L
        entries.forEach { entry -> deserialize(entry)?.let { handler(it); processed++ } }
        log.info("Replayed {}/{} incremental events (after seq {})", processed, entries.size, afterSequence)
        return processed
    }

    /**
     * Replay all events for a single aggregate.
     * Returns the number of events successfully processed.
     */
    fun replayAggregate(aggregateId: java.util.UUID, handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findByAggregateId(aggregateId)
        var processed = 0L
        entries.forEach { entry -> deserialize(entry)?.let { handler(it); processed++ } }
        return processed
    }

    private fun deserialize(entry: EventLogEntry): DomainEvent? {
        val clazz = eventTypeMap[entry.eventType]
        if (clazz == null) {
            log.warn("Unknown event type '{}' at sequence {}", entry.eventType, entry.sequenceNumber)
            return null
        }
        return try {
            objectMapper.readValue(entry.payload, clazz)
        } catch (e: Exception) {
            log.error("Failed to deserialize event seq={} type={}: {}", entry.sequenceNumber, entry.eventType, e.message)
            null
        }
    }
}
