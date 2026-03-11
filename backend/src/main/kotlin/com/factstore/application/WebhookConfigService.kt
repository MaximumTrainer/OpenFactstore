package com.factstore.application

import com.factstore.core.domain.WebhookConfig
import com.factstore.core.port.inbound.IWebhookConfigService
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IWebhookConfigRepository
import com.factstore.core.port.outbound.IWebhookDeliveryRepository
import com.factstore.dto.CreateWebhookConfigRequest
import com.factstore.dto.WebhookConfigResponse
import com.factstore.dto.WebhookDeliveryResponse
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

@Service
@Transactional
class WebhookConfigService(
    private val webhookConfigRepository: IWebhookConfigRepository,
    private val webhookDeliveryRepository: IWebhookDeliveryRepository,
    private val flowRepository: IFlowRepository
) : IWebhookConfigService {

    private val log = LoggerFactory.getLogger(WebhookConfigService::class.java)

    override fun createConfig(request: CreateWebhookConfigRequest): WebhookConfigResponse {
        if (!flowRepository.existsById(request.flowId)) {
            throw NotFoundException("Flow not found: ${request.flowId}")
        }
        val config = WebhookConfig(
            source = request.source,
            secretHash = hashSecret(request.secret),
            flowId = request.flowId
        )
        val saved = webhookConfigRepository.save(config)
        log.info("Created webhook config: ${saved.id} source=${saved.source} flowId=${saved.flowId}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listConfigs(): List<WebhookConfigResponse> =
        webhookConfigRepository.findAll().map { it.toResponse() }

    override fun deleteConfig(id: UUID) {
        if (!webhookConfigRepository.existsById(id)) {
            throw NotFoundException("Webhook config not found: $id")
        }
        webhookConfigRepository.deleteById(id)
        log.info("Deleted webhook config: $id")
    }

    @Transactional(readOnly = true)
    override fun listDeliveries(configId: UUID): List<WebhookDeliveryResponse> {
        if (!webhookConfigRepository.existsById(configId)) {
            throw NotFoundException("Webhook config not found: $configId")
        }
        return webhookDeliveryRepository.findByWebhookConfigId(configId).map { it.toResponse() }
    }

    companion object {
        fun hashSecret(secret: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

fun WebhookConfig.toResponse() = WebhookConfigResponse(
    id = id,
    source = source,
    flowId = flowId,
    isActive = isActive,
    createdAt = createdAt
)

fun com.factstore.core.domain.WebhookDelivery.toResponse() = WebhookDeliveryResponse(
    id = id,
    webhookConfigId = webhookConfigId,
    deliveryId = deliveryId,
    source = source,
    eventType = eventType,
    status = status,
    statusMessage = statusMessage,
    receivedAt = receivedAt
)
