package com.factstore.adapter.inbound.messaging

import com.factstore.application.ReadModelProjector
import com.factstore.config.RabbitMqConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

/**
 * Consumes domain events from the RabbitMQ projection queue and delegates
 * them to the [ReadModelProjector] to update the read database.
 *
 * Active when `factstore.events.publisher=rabbitmq`.  In production the
 * query service runs this consumer while the command service runs the
 * corresponding publisher.
 */
@Component
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "rabbitmq")
class RabbitMqEventConsumer(
    private val projector: ReadModelProjector
) {

    private val log = LoggerFactory.getLogger(RabbitMqEventConsumer::class.java)

    @RabbitListener(queues = [RabbitMqConfig.QUEUE_NAME])
    fun onEvent(
        payload: String,
        @Header("amqp_receivedRoutingKey") routingKey: String
    ) {
        val eventType = routingKey.substringAfterLast(".")
        log.info("Received domain event from RabbitMQ: type={}", eventType)
        projector.project(eventType, payload)
    }
}
