package com.factstore.adapter.outbound

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationRule
import com.factstore.core.port.outbound.INotificationChannel
import com.factstore.dto.NotificationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class WebhookNotificationChannel(
    restTemplateBuilder: RestTemplateBuilder,
    private val objectMapper: ObjectMapper
) : INotificationChannel {

    override val channelType = ChannelType.WEBHOOK

    private val log = LoggerFactory.getLogger(WebhookNotificationChannel::class.java)
    private val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .build()

    override fun send(rule: NotificationRule, event: NotificationEvent) {
        val config = objectMapper.readValue(rule.channelConfig, Map::class.java)
        val webhookUrl = config["webhookUrl"] as? String
            ?: throw IllegalArgumentException("WebhookChannel requires 'webhookUrl' in channelConfig")

        UrlValidator.validate(webhookUrl)

        val payload = mapOf(
            "triggerEvent" to event.triggerEvent.name,
            "title" to event.title,
            "message" to event.message,
            "severity" to event.severity.name,
            "entityType" to event.entityType,
            "entityId" to event.entityId?.toString(),
            "filterFlowId" to event.filterFlowId?.toString(),
            "extraPayload" to event.extraPayload
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(objectMapper.writeValueAsString(payload), headers)

        val response = restTemplate.postForEntity(webhookUrl, entity, String::class.java)
        if (!response.statusCode.is2xxSuccessful) {
            throw RuntimeException("Webhook endpoint returned ${response.statusCode}")
        }
        log.info("Sent webhook notification for rule=${rule.id} event=${event.triggerEvent}")
    }
}
