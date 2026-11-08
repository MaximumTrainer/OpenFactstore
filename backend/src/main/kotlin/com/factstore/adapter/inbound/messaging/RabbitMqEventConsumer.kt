package com.factstore.adapter.inbound.messaging

import com.factstore.application.ReadModelProjector
import com.factstore.application.observability.ProjectionFailureRecorder
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
    private val projector: ReadModelProjector,
    private val failureRecorder: ProjectionFailureRecorder
) {

    private val log = LoggerFactory.getLogger(RabbitMqEventConsumer::class.java)

    /**
     * A message the projector cannot apply is not acknowledged as success (#185): it is recorded
     * in `projection_failures`, with its payload, and counted, and only then acknowledged. A
     * poison message is therefore neither lost nor redelivered forever. If recording itself fails,
     * the exception propagates and the broker redelivers.
     */
    @RabbitListener(queues = [RabbitMqConfig.PROJECTION_QUEUE_NAME])
    fun onEvent(entry: EventLogEntry) {
        log.info("Received domain event from RabbitMQ: type={} seq={}", entry.eventType, entry.sequenceNumber)
        val projected = try {
            projector.project(entry.eventType, entry.payload)
        } catch (e: Exception) {
            failureRecorder.record(entry, "projector threw ${e::class.simpleName}: ${e.message}")
            return
        }
        if (!projected) {
            failureRecorder.record(entry, "projector could not apply the event (unknown type or unreadable payload)")
        }
    }
}
