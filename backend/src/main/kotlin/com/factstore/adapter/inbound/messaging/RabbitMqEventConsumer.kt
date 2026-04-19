package com.factstore.adapter.inbound.messaging

import com.factstore.application.ReadModelProjector
import com.factstore.config.RabbitMqConfig
import com.factstore.core.domain.EventLogEntry
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Consumes domain events from the RabbitMQ projection queue and delegates
 * them to the [ReadModelProjector] to update the read database.
 *
 * Gated on `factstore.cqrs.role=query` so that only the query service
 * instance starts consuming from the projection queue.  Without this
 * guard both the command and query containers (which share the same JAR
 * and `prod` profile) would compete for messages, causing projections to
 * execute against the wrong database.
 */
@Component
@ConditionalOnProperty(name = ["factstore.cqrs.role"], havingValue = "query")
class RabbitMqEventConsumer(
    private val projector: ReadModelProjector
) {

    private val log = LoggerFactory.getLogger(RabbitMqEventConsumer::class.java)

    @RabbitListener(queues = [RabbitMqConfig.PROJECTION_QUEUE_NAME])
    fun onEvent(entry: EventLogEntry) {
        try {
            log.info("Received domain event from RabbitMQ: type={} seq={}", entry.eventType, entry.sequenceNumber)
            val projected = projector.project(entry.eventType, entry.payload)
            if (!projected) {
                log.error("Failed to project event type={} seq={} — message acknowledged but data may be stale. " +
                        "Replay from the event store to recover.", entry.eventType, entry.sequenceNumber)
            }
        } catch (e: Exception) {
            log.error("Unexpected error processing domain event: {}", e.message, e)
            throw e
        }
    }
}
