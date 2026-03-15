package com.factstore.adapter.outbound.events

import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.SupplyChainEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "none")
class NoopEventPublisher : IEventPublisher {
    override fun publish(event: SupplyChainEvent) { /* intentionally silent */ }
}
