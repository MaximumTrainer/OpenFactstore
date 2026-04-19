package com.factstore.adapter.outbound.events

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IDomainEventBus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * No-op domain event bus for the `none` publisher profile.
 * See [NoopDomainEventBus] for the `logging` (default) profile.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "none")
class NoopDomainEventBusNone : IDomainEventBus {
    override fun publish(entry: EventLogEntry) { /* no-op */ }
}
