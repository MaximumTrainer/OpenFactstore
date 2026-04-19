package com.factstore.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RabbitMQ infrastructure for the CQRS event feed.
 *
 * Active only when `factstore.events.publisher=rabbitmq`.  The command
 * service publishes domain events to a dedicated topic exchange; the query
 * service consumes them from the bound queue and projects into its read
 * database.
 *
 * Supply-chain events ([com.factstore.core.port.outbound.SupplyChainEvent])
 * are published to a separate exchange to avoid polluting the projection
 * queue with messages the [com.factstore.application.ReadModelProjector]
 * cannot deserialize.
 */
@Configuration
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "rabbitmq")
class RabbitMqConfig {

    companion object {
        /** Dedicated exchange for CQRS domain events (EventLogEntry payloads). */
        const val DOMAIN_EXCHANGE_NAME = "factstore.domain-events"
        const val PROJECTION_QUEUE_NAME = "factstore.events.projection"
        const val DOMAIN_ROUTING_KEY = "cqrs.domain.event.#"

        /** Separate exchange for supply-chain events (webhooks, notifications). */
        const val SUPPLY_CHAIN_EXCHANGE_NAME = "factstore.supply-chain-events"
        const val SUPPLY_CHAIN_ROUTING_KEY_PREFIX = "supply-chain.event."
    }

    // ── Domain event infrastructure ────────────────────────────────────────

    @Bean
    fun domainEventExchange(): TopicExchange = TopicExchange(DOMAIN_EXCHANGE_NAME)

    @Bean
    fun projectionQueue(): Queue = Queue(PROJECTION_QUEUE_NAME, true)

    @Bean
    fun projectionBinding(projectionQueue: Queue, domainEventExchange: TopicExchange): Binding =
        BindingBuilder.bind(projectionQueue).to(domainEventExchange).with(DOMAIN_ROUTING_KEY)

    // ── Supply-chain event infrastructure ──────────────────────────────────

    @Bean
    fun supplyChainExchange(): TopicExchange = TopicExchange(SUPPLY_CHAIN_EXCHANGE_NAME)

    @Bean
    fun jacksonMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}
