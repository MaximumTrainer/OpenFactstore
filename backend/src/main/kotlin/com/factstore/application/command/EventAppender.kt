package com.factstore.application.command

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * Converts a [DomainEvent] into an [EventLogEntry] and appends it to the
 * event store. Keeps the command-handler code focused on business logic.
 */
@Component
class EventAppender(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper
) {
    fun append(event: DomainEvent) {
        val entry = EventLogEntry(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event::class.simpleName ?: "Unknown",
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt
        )
        eventStore.append(entry)
    }
}
