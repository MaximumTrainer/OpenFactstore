package com.factstore.application

import com.factstore.core.domain.NotificationDelivery
import com.factstore.core.domain.NotificationDeliveryStatus
import com.factstore.core.domain.NotificationRule
import com.factstore.core.port.outbound.INotificationChannel
import com.factstore.core.port.outbound.INotificationDeliveryRepository
import com.factstore.core.port.outbound.INotificationRuleRepository
import com.factstore.dto.NotificationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class NotificationDispatchService(
    private val notificationRuleRepository: INotificationRuleRepository,
    private val notificationDeliveryRepository: INotificationDeliveryRepository,
    private val channels: List<INotificationChannel>,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(NotificationDispatchService::class.java)
    private val maxAttempts = 3

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun dispatch(event: NotificationEvent) {
        val matchingRules = notificationRuleRepository
            .findAllActiveByTriggerEvent(event.triggerEvent)
            .filter { ruleMatchesEvent(it, event) }

        for (rule in matchingRules) {
            dispatchToRule(rule, event)
        }
    }

    private fun ruleMatchesEvent(rule: NotificationRule, event: NotificationEvent): Boolean {
        if (rule.filterFlowId != null && rule.filterFlowId != event.filterFlowId) return false
        if (rule.filterEnvironmentId != null && rule.filterEnvironmentId != event.filterEnvironmentId) return false
        return true
    }

    private fun dispatchToRule(rule: NotificationRule, event: NotificationEvent) {
        val channel = channels.find { it.channelType == rule.channelType }
        if (channel == null) {
            log.warn("No channel implementation found for type=${rule.channelType} rule=${rule.id}")
            saveDelivery(rule, event, NotificationDeliveryStatus.SKIPPED, 0,
                "No channel implementation for ${rule.channelType}")
            return
        }

        var lastError: String? = null
        for (attempt in 1..maxAttempts) {
            try {
                channel.send(rule, event)
                saveDelivery(rule, event, NotificationDeliveryStatus.SENT, attempt, null)
                return
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                log.warn("Attempt $attempt/$maxAttempts failed for rule=${rule.id}: $lastError")
                if (attempt < maxAttempts) {
                    Thread.sleep(exponentialBackoffMs(attempt))
                }
            }
        }

        log.error("All $maxAttempts attempts failed for rule=${rule.id}. Dead-lettering delivery.")
        saveDelivery(rule, event, NotificationDeliveryStatus.FAILED, maxAttempts, lastError)
    }

    private fun saveDelivery(
        rule: NotificationRule,
        event: NotificationEvent,
        status: NotificationDeliveryStatus,
        attemptCount: Int,
        error: String?
    ) {
        val payload = try {
            objectMapper.writeValueAsString(
                mapOf(
                    "triggerEvent" to event.triggerEvent.name,
                    "title" to event.title,
                    "message" to event.message,
                    "entityType" to event.entityType,
                    "entityId" to event.entityId?.toString()
                )
            )
        } catch (_: Exception) { null }

        notificationDeliveryRepository.save(
            NotificationDelivery(
                ruleId = rule.id,
                eventType = event.triggerEvent.name,
                payload = payload,
                status = status,
                sentAt = Instant.now(),
                error = error,
                attemptCount = attemptCount
            )
        )
    }

    private fun exponentialBackoffMs(attempt: Int): Long =
        Duration.ofSeconds(BASE_BACKOFF_SECONDS.shl(attempt - 1)).toMillis()

    companion object {
        private const val BASE_BACKOFF_SECONDS = 2L
    }
}
