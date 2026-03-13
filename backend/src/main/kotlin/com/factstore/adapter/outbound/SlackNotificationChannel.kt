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
class SlackNotificationChannel(
    restTemplateBuilder: RestTemplateBuilder,
    private val objectMapper: ObjectMapper
) : INotificationChannel {

    override val channelType = ChannelType.SLACK

    private val log = LoggerFactory.getLogger(SlackNotificationChannel::class.java)
    private val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .build()

    override fun send(rule: NotificationRule, event: NotificationEvent) {
        val config = objectMapper.readValue(rule.channelConfig, Map::class.java)
        val webhookUrl = config["webhookUrl"] as? String
            ?: throw IllegalArgumentException("SlackChannel requires 'webhookUrl' in channelConfig")

        UrlValidator.validate(webhookUrl)

        val text = buildSlackMessage(event)
        val body = mapOf("text" to text)

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(webhookUrl, entity, String::class.java)
        if (!response.statusCode.is2xxSuccessful) {
            throw RuntimeException("Slack webhook returned ${response.statusCode}")
        }
        log.info("Sent Slack notification for rule=${rule.id} event=${event.triggerEvent}")
    }

    private fun buildSlackMessage(event: NotificationEvent): String {
        val sb = StringBuilder()
        sb.appendLine("🚨 *Compliance Alert* — Factstore")
        sb.appendLine("*Type*: ${event.triggerEvent.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}")
        sb.appendLine("*Title*: ${event.title}")
        sb.appendLine("*Message*: ${event.message}")
        if (event.entityType != null) sb.appendLine("*Entity*: ${event.entityType} `${event.entityId}`")
        event.extraPayload.forEach { (k, v) -> if (v != null) sb.appendLine("*${k}*: $v") }
        return sb.toString().trimEnd()
    }
}
