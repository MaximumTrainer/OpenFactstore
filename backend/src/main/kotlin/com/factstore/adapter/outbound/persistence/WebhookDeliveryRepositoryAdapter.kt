package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.WebhookDelivery
import com.factstore.core.port.outbound.IWebhookDeliveryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WebhookDeliveryRepositoryJpa : JpaRepository<WebhookDelivery, UUID> {
    fun findByWebhookConfigId(webhookConfigId: UUID): List<WebhookDelivery>
    fun existsByDeliveryIdAndWebhookConfigId(deliveryId: String, webhookConfigId: UUID): Boolean
}

@Component
class WebhookDeliveryRepositoryAdapter(private val jpa: WebhookDeliveryRepositoryJpa) : IWebhookDeliveryRepository {
    override fun save(delivery: WebhookDelivery): WebhookDelivery = jpa.save(delivery)
    override fun findByWebhookConfigId(webhookConfigId: UUID): List<WebhookDelivery> = jpa.findByWebhookConfigId(webhookConfigId)
    override fun existsByDeliveryIdAndWebhookConfigId(deliveryId: String, webhookConfigId: UUID): Boolean =
        jpa.existsByDeliveryIdAndWebhookConfigId(deliveryId, webhookConfigId)
}
