package com.factstore.core.port.inbound

import com.factstore.dto.CreateWebhookConfigRequest
import com.factstore.dto.WebhookConfigResponse
import com.factstore.dto.WebhookDeliveryResponse
import java.util.UUID

interface IWebhookConfigService {
    fun createConfig(request: CreateWebhookConfigRequest): WebhookConfigResponse
    fun listConfigs(): List<WebhookConfigResponse>
    fun deleteConfig(id: UUID)
    fun listDeliveries(configId: UUID): List<WebhookDeliveryResponse>
}
