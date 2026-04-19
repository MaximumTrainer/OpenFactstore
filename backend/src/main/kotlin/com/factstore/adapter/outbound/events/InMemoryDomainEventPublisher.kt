package com.factstore.adapter.outbound.events

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IDomainEventBus
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * In-memory domain event bus backed by Spring's [ApplicationEventPublisher].
 *
 * Active when `factstore.events.publisher=inmemory`.  The query-side
 * [com.factstore.application.ReadModelProjector] listens for these events
 * and applies projections within the same JVM — perfect for integration
 * tests that do not require an external message broker.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "inmemory")
class InMemoryDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : IDomainEventBus {

    private val log = LoggerFactory.getLogger(InMemoryDomainEventPublisher::class.java)

    override fun publish(entry: EventLogEntry) {
        log.debug("Publishing domain event seq={} type={} via in-memory bus",
            entry.sequenceNumber, entry.eventType)
        applicationEventPublisher.publishEvent(entry)
    }
}
