package com.factstore.application.command

import com.factstore.application.observability.FailureMetrics
import com.factstore.application.signing.EventRecordSealer
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IDomainEventBus
import com.factstore.core.port.outbound.IEventOutbox
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
 *
 * **A failed publish is not dropped (#185).** The entry is also recorded in the
 * [IEventOutbox] inside the same transaction, and removed only once the publish succeeds. If
 * the broker is down the entry stays there, the failure is counted, and [EventOutboxRelay]
 * re-publishes it once the broker is reachable again. Delivery is therefore at-least-once;
 * the read-model projector is idempotent.
 */
@Component
class EventAppender(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper,
    private val domainEventBus: IDomainEventBus,
    private val outbox: IEventOutbox,
    private val failureMetrics: FailureMetrics,
    private val sealer: EventRecordSealer
) {
    private val log = LoggerFactory.getLogger(EventAppender::class.java)

    /** Appends and signs [event]; returns the stored record, whose `recordHash` is its receipt (#186). */
    fun append(event: DomainEvent): EventLogEntry {
        val entry = EventLogEntry(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event::class.simpleName ?: "Unknown",
            payload = objectMapper.writeValueAsString(event),
            // Stored at the database's precision, so the signed timestamp (#184) is exactly the
            // stored one: the database rounds sub-microsecond values, the signature must not depend on it.
            occurredAt = event.occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        )
        // Inserted and signed in this transaction (#184): a signing failure rolls the insert back.
        val saved = sealer.appendSealed(entry)

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            outbox.enqueue(saved.sequenceNumber)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = publishAfterCommit(saved)
            })
        } else {
            // No active transaction (e.g. in unit tests) — publish immediately.
            domainEventBus.publish(saved)
        }
        return saved
    }

    private fun publishAfterCommit(saved: EventLogEntry) {
        try {
            domainEventBus.publish(saved)
        } catch (e: Exception) {
            failureMetrics.eventPublishFailure()
            runCatching { outbox.recordFailure(saved.sequenceNumber, e.message ?: e::class.java.name) }
            log.error(
                "Failed to publish domain event seq={} type={} eventId={} aggregateId={} after commit; " +
                    "held in the outbox for the relay to retry",
                saved.sequenceNumber,
                saved.eventType,
                saved.eventId,
                saved.aggregateId,
                e
            )
            return
        }
        try {
            outbox.remove(saved.sequenceNumber)
        } catch (e: Exception) {
            // The event was delivered; at worst the relay delivers it again, which the idempotent
            // projector tolerates.
            log.warn("Published seq={} but could not clear its outbox entry: {}", saved.sequenceNumber, e.message)
        }
    }
}
