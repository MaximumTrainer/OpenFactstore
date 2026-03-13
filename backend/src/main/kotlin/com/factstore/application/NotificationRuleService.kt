package com.factstore.application

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationRule
import com.factstore.core.port.inbound.INotificationRuleService
import com.factstore.core.port.outbound.INotificationDeliveryRepository
import com.factstore.core.port.outbound.INotificationRuleRepository
import com.factstore.dto.CreateNotificationRuleRequest
import com.factstore.dto.NotificationDeliveryResponse
import com.factstore.dto.NotificationEvent
import com.factstore.dto.NotificationRuleResponse
import com.factstore.dto.UpdateNotificationRuleRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class NotificationRuleService(
    private val notificationRuleRepository: INotificationRuleRepository,
    private val notificationDeliveryRepository: INotificationDeliveryRepository,
    private val notificationDispatchService: NotificationDispatchService,
    private val objectMapper: ObjectMapper
) : INotificationRuleService {

    private val log = LoggerFactory.getLogger(NotificationRuleService::class.java)

    override fun createRule(request: CreateNotificationRuleRequest): NotificationRuleResponse {
        validateChannelConfig(request.channelType, request.channelConfig)
        val rule = NotificationRule(
            name = request.name,
            triggerEvent = request.triggerEvent,
            channelType = request.channelType,
            channelConfig = request.channelConfig,
            filterFlowId = request.filterFlowId,
            filterEnvironmentId = request.filterEnvironmentId
        )
        val saved = notificationRuleRepository.save(rule)
        log.info("Created notification rule: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listRules(): List<NotificationRuleResponse> =
        notificationRuleRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getRule(id: UUID): NotificationRuleResponse =
        (notificationRuleRepository.findById(id)
            ?: throw NotFoundException("Notification rule not found: $id")).toResponse()

    override fun updateRule(id: UUID, request: UpdateNotificationRuleRequest): NotificationRuleResponse {
        val rule = notificationRuleRepository.findById(id)
            ?: throw NotFoundException("Notification rule not found: $id")
        request.name?.let { rule.name = it }
        request.isActive?.let { rule.isActive = it }
        request.triggerEvent?.let { rule.triggerEvent = it }
        request.channelType?.let { rule.channelType = it }
        request.channelConfig?.let {
            validateChannelConfig(request.channelType ?: rule.channelType, it)
            rule.channelConfig = it
        }
        rule.filterFlowId = when {
            request.clearFilterFlowId -> null
            request.filterFlowId != null -> request.filterFlowId
            else -> rule.filterFlowId
        }
        rule.filterEnvironmentId = when {
            request.clearFilterEnvironmentId -> null
            request.filterEnvironmentId != null -> request.filterEnvironmentId
            else -> rule.filterEnvironmentId
        }
        rule.updatedAt = Instant.now()
        return notificationRuleRepository.save(rule).toResponse()
    }

    override fun deleteRule(id: UUID) {
        if (!notificationRuleRepository.existsById(id)) {
            throw NotFoundException("Notification rule not found: $id")
        }
        notificationRuleRepository.deleteById(id)
        log.info("Deleted notification rule: $id")
    }

    override fun testRule(id: UUID) {
        val rule = notificationRuleRepository.findById(id)
            ?: throw NotFoundException("Notification rule not found: $id")
        val testEvent = NotificationEvent(
            triggerEvent = rule.triggerEvent,
            title = "Test notification",
            message = "This is a test notification for rule '${rule.name}'",
            filterFlowId = rule.filterFlowId,
            filterEnvironmentId = rule.filterEnvironmentId
        )
        notificationDispatchService.dispatch(testEvent)
        log.info("Triggered test notification for rule: ${rule.id}")
    }

    @Transactional(readOnly = true)
    override fun getRuleDeliveries(id: UUID): List<NotificationDeliveryResponse> {
        if (!notificationRuleRepository.existsById(id)) {
            throw NotFoundException("Notification rule not found: $id")
        }
        return notificationDeliveryRepository.findByRuleId(id).map { it.toResponse() }
    }

    private fun validateChannelConfig(channelType: ChannelType, channelConfig: String) {
        if (channelType == ChannelType.SLACK || channelType == ChannelType.WEBHOOK) {
            val config = try {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(channelConfig, Map::class.java) as Map<String, Any?>
            } catch (e: Exception) {
                throw BadRequestException("channelConfig must be valid JSON for channel type $channelType")
            }
            val webhookUrl = config["webhookUrl"] as? String
            if (webhookUrl.isNullOrBlank()) {
                throw BadRequestException(
                    "channelConfig must contain a non-empty 'webhookUrl' for channel type $channelType"
                )
            }
        }
    }
}

fun NotificationRule.toResponse() = NotificationRuleResponse(
    id = id,
    name = name,
    isActive = isActive,
    triggerEvent = triggerEvent,
    channelType = channelType,
    channelConfig = channelConfig,
    filterFlowId = filterFlowId,
    filterEnvironmentId = filterEnvironmentId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
