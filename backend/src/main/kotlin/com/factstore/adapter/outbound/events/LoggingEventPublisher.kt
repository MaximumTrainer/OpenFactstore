package com.factstore.adapter.outbound.events

import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.SupplyChainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "logging", matchIfMissing = true)
class LoggingEventPublisher(private val objectMapper: ObjectMapper) : IEventPublisher {
    private val log = LoggerFactory.getLogger(LoggingEventPublisher::class.java)

    override fun publish(event: SupplyChainEvent) {
        val json = objectMapper.writeValueAsString(mapOf(
            "eventType" to event::class.simpleName,
            "eventId" to event.id,
            "occurredAt" to event.occurredAt,
            "orgSlug" to event.orgSlug,
            "payload" to event
        ))
        log.info("SUPPLY_CHAIN_EVENT {}", json)
    }
}
