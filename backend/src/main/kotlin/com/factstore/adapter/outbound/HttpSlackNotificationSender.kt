package com.factstore.adapter.outbound

import com.factstore.core.port.outbound.ISlackNotificationSender
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpSlackNotificationSender(
    restTemplateBuilder: RestTemplateBuilder
) : ISlackNotificationSender {

    private val log = LoggerFactory.getLogger(HttpSlackNotificationSender::class.java)
    private val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .build()

    override fun send(botToken: String, channel: String, message: String): Boolean {
        return try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("Authorization", "Bearer $botToken")
            }
            val body = mapOf("channel" to channel, "text" to message)
            val entity = HttpEntity(body, headers)
            val response = restTemplate.postForEntity(
                "https://slack.com/api/chat.postMessage",
                entity,
                Map::class.java
            )
            @Suppress("UNCHECKED_CAST")
            val responseBody = response.body as? Map<*, *>
            val ok = responseBody?.get("ok") as? Boolean ?: false
            if (!ok) {
                val error = responseBody?.get("error") as? String
                if (error != null) {
                    log.error("Slack API returned ok=false when sending to channel $channel: $error")
                } else {
                    log.error("Slack API returned ok=false with no error when sending to channel $channel")
                }
                return false
            }
            log.info("Sent Slack message to channel: $channel")
            true
        } catch (e: Exception) {
            log.error("Failed to send Slack message to channel $channel: ${e.message}", e)
            false
        }
    }
}
