package com.factstore.core.port.outbound

import com.factstore.core.domain.WebhookConfig
import java.util.UUID

interface IWebhookConfigRepository {
    fun save(config: WebhookConfig): WebhookConfig
    fun findById(id: UUID): WebhookConfig?
    fun findAll(): List<WebhookConfig>
    fun findByFlowId(flowId: UUID): List<WebhookConfig>
    fun deleteById(id: UUID)
    fun existsById(id: UUID): Boolean
}
