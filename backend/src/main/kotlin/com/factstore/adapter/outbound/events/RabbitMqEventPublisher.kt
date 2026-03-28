package com.factstore.adapter.outbound.events

import com.factstore.config.RabbitMqConfig
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.SupplyChainEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Publishes [SupplyChainEvent]s to RabbitMQ so that the query service can
 * project them into its read database.
 *
 * Active when `factstore.events.publisher=rabbitmq`.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "rabbitmq")
class RabbitMqEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) : IEventPublisher {

    private val log = LoggerFactory.getLogger(RabbitMqEventPublisher::class.java)

    override fun publish(event: SupplyChainEvent) {
        val routingKey = "domain.event.${event::class.simpleName}"
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, routingKey, event)
        log.info("Published event {} to RabbitMQ exchange={} routingKey={}",
            event.id, RabbitMqConfig.EXCHANGE_NAME, routingKey)
    }
}
