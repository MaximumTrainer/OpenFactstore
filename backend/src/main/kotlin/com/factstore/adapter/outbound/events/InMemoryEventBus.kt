package com.factstore.adapter.outbound.events

import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.SupplyChainEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * In-memory event bus backed by Spring's [ApplicationEventPublisher].
 *
 * Active when `factstore.events.publisher=inmemory`.  Designed for
 * integration tests so they can verify event-driven projections without
 * requiring an external message broker.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "inmemory")
class InMemoryEventBus(
    private val applicationEventPublisher: ApplicationEventPublisher
) : IEventPublisher {

    private val log = LoggerFactory.getLogger(InMemoryEventBus::class.java)

    override fun publish(event: SupplyChainEvent) {
        log.debug("Publishing event {} via in-memory bus", event.id)
        applicationEventPublisher.publishEvent(event)
    }
}
