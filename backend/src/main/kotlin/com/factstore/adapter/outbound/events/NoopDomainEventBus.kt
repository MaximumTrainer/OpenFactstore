package com.factstore.adapter.outbound.events

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IDomainEventBus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Default no-op domain event bus.  Active when the event publisher is set
 * to `logging` (the default) or `none` — i.e. no CQRS replication is
 * configured.  Command-side events are still persisted to the event store
 * but are not forwarded to any query-side consumer.
 */
@Component
@ConditionalOnProperty(
    name = ["factstore.events.publisher"],
    havingValue = "logging",
    matchIfMissing = true
)
class NoopDomainEventBus : IDomainEventBus {
    override fun publish(entry: EventLogEntry) { /* no-op */ }
}
