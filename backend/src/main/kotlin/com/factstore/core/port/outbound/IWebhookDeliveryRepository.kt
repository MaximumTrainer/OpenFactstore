package com.factstore.core.port.outbound

import com.factstore.core.domain.WebhookDelivery
import java.util.UUID

interface IWebhookDeliveryRepository {
    fun save(delivery: WebhookDelivery): WebhookDelivery
    fun findByWebhookConfigId(webhookConfigId: UUID): List<WebhookDelivery>
    fun existsByDeliveryIdAndWebhookConfigId(deliveryId: String, webhookConfigId: UUID): Boolean
}
