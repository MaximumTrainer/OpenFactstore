package com.factstore.adapter.outbound.events

import com.factstore.config.RabbitMqConfig
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IDomainEventBus
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Publishes [EventLogEntry] records to the dedicated CQRS domain-event
 * exchange so that the query service can project them into its read
 * database.
 *
 * The full [EventLogEntry] (including `eventType` and `payload`) is sent
 * as a structured JSON message, avoiding double-encoding issues that would
 * occur if only the raw payload string were sent through the Jackson
 * message converter.
 *
 * Active when `factstore.events.publisher=rabbitmq`.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "rabbitmq")
class RabbitMqDomainEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) : IDomainEventBus {

    private val log = LoggerFactory.getLogger(RabbitMqDomainEventPublisher::class.java)

    override fun publish(entry: EventLogEntry) {
        val routingKey = "cqrs.domain.event.${entry.eventType}"
        rabbitTemplate.convertAndSend(RabbitMqConfig.DOMAIN_EXCHANGE_NAME, routingKey, entry)
        log.info("Published domain event seq={} type={} to RabbitMQ",
            entry.sequenceNumber, entry.eventType)
    }
}
