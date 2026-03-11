package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.WebhookConfig
import com.factstore.core.port.outbound.IWebhookConfigRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WebhookConfigRepositoryJpa : JpaRepository<WebhookConfig, UUID> {
    fun findByFlowId(flowId: UUID): List<WebhookConfig>
}

@Component
class WebhookConfigRepositoryAdapter(private val jpa: WebhookConfigRepositoryJpa) : IWebhookConfigRepository {
    override fun save(config: WebhookConfig): WebhookConfig = jpa.save(config)
    override fun findById(id: UUID): WebhookConfig? = jpa.findById(id).orElse(null)
    override fun findAll(): List<WebhookConfig> = jpa.findAll()
    override fun findByFlowId(flowId: UUID): List<WebhookConfig> = jpa.findByFlowId(flowId)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
}
