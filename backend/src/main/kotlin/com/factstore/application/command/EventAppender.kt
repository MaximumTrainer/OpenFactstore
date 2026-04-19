package com.factstore.application.command

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IDomainEventBus
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Converts a [DomainEvent] into an [EventLogEntry] and appends it to the
 * event store.  After the surrounding transaction **commits**, the entry
 * is published to the [IDomainEventBus] so that the query service can
 * project it into the read database.
 *
 * Publishing is deferred via [TransactionSynchronizationManager] to
 * prevent phantom events:  if the transaction rolls back, the RabbitMQ
 * message is never sent, keeping the write and read sides consistent.
 */
@Component
class EventAppender(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper,
    private val domainEventBus: IDomainEventBus
) {
    private val log = LoggerFactory.getLogger(EventAppender::class.java)

    fun append(event: DomainEvent) {
        val entry = EventLogEntry(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event::class.simpleName ?: "Unknown",
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt
        )
        val saved = eventStore.append(entry)

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        domainEventBus.publish(saved)
                    } catch (e: Exception) {
                        log.error(
                            "Failed to publish domain event seq={} type={} eventId={} aggregateId={} after commit — " +
                                "replay from the event store to recover",
                            saved.sequenceNumber,
                            saved.eventType,
                            saved.eventId,
                            saved.aggregateId,
                            e
                        )
                    }
                }
            })
        } else {
            // No active transaction (e.g. in unit tests) — publish immediately.
            domainEventBus.publish(saved)
        }
    }
}
