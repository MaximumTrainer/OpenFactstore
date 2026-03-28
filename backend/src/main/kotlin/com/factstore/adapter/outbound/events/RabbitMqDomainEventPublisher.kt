package com.factstore.adapter.outbound.events

import com.factstore.config.RabbitMqConfig
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IDomainEventBus
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Publishes serialised [EventLogEntry] records to the RabbitMQ topic
 * exchange so that the query service can project them into its read
 * database.
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
        val routingKey = "domain.event.${entry.eventType}"
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, routingKey, entry.payload)
        log.info("Published domain event seq={} type={} to RabbitMQ",
            entry.sequenceNumber, entry.eventType)
    }
}
