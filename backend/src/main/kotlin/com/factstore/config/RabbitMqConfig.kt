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
 * service publishes domain events to the topic exchange; the query service
 * consumes them from the bound queue and projects into its read database.
 */
@Configuration
@ConditionalOnProperty(name = ["factstore.events.publisher"], havingValue = "rabbitmq")
class RabbitMqConfig {

    companion object {
        const val EXCHANGE_NAME = "factstore.events"
        const val QUEUE_NAME = "factstore.events.projection"
        const val ROUTING_KEY = "domain.event.#"
    }

    @Bean
    fun eventExchange(): TopicExchange = TopicExchange(EXCHANGE_NAME)

    @Bean
    fun projectionQueue(): Queue = Queue(QUEUE_NAME, true)

    @Bean
    fun projectionBinding(projectionQueue: Queue, eventExchange: TopicExchange): Binding =
        BindingBuilder.bind(projectionQueue).to(eventExchange).with(ROUTING_KEY)

    @Bean
    fun jacksonMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}
