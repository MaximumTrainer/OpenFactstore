package com.factstore.adapter.inbound.messaging

import com.factstore.application.ReadModelProjector
import com.factstore.core.domain.EventLogEntry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for [EventLogEntry] instances published by the
 * [com.factstore.adapter.outbound.events.InMemoryDomainEventPublisher] and
 * feeds them to the [ReadModelProjector].
 *
 * Active when `factstore.events.publisher=inmemory`.  This allows
 * integration tests to exercise the full CQRS event-driven projection
 * pipeline without an external message broker.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "inmemory")
class InMemoryEventListener(
    private val projector: ReadModelProjector
) {

    private val log = LoggerFactory.getLogger(InMemoryEventListener::class.java)

    @EventListener
    fun onDomainEvent(entry: EventLogEntry) {
        log.debug("In-memory event received: type={} seq={}", entry.eventType, entry.sequenceNumber)
        projector.project(entry.eventType, entry.payload)
    }
}
