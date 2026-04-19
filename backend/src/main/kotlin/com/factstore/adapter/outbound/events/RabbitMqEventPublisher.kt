package com.factstore.adapter.outbound.events

import com.factstore.config.RabbitMqConfig
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.SupplyChainEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Publishes [SupplyChainEvent]s to a dedicated RabbitMQ exchange for
 * external consumers (e.g. webhooks, notification pipelines).
 *
 * Uses a **separate exchange** from the CQRS domain-event feed
 * ([RabbitMqDomainEventPublisher]) so that supply-chain messages never
 * reach the projection queue and cause deserialization failures.
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
        val routingKey = "${RabbitMqConfig.SUPPLY_CHAIN_ROUTING_KEY_PREFIX}${event::class.simpleName}"
        rabbitTemplate.convertAndSend(RabbitMqConfig.SUPPLY_CHAIN_EXCHANGE_NAME, routingKey, event)
        log.info("Published supply-chain event {} to RabbitMQ exchange={} routingKey={}",
            event.id, RabbitMqConfig.SUPPLY_CHAIN_EXCHANGE_NAME, routingKey)
    }
}
