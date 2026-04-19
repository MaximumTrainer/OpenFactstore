package com.factstore.core.port.outbound

import com.factstore.core.domain.EventLogEntry

/**
 * Outbound port for the CQRS event feed.
 *
 * After a domain event is persisted to the write-side event store, it is
 * published through this bus so the query service can project it into the
 * read database.  Implementations may use RabbitMQ (production) or an
 * in-memory Spring event (tests).
 */
interface IDomainEventBus {
    fun publish(entry: EventLogEntry)
}
