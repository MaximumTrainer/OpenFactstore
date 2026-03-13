package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.NotificationDelivery
import com.factstore.core.port.outbound.INotificationDeliveryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationDeliveryRepositoryJpa : JpaRepository<NotificationDelivery, UUID> {
    fun findAllByRuleIdOrderBySentAtDesc(ruleId: UUID): List<NotificationDelivery>
}

@Component
class NotificationDeliveryRepositoryAdapter(private val jpa: NotificationDeliveryRepositoryJpa) : INotificationDeliveryRepository {
    override fun save(delivery: NotificationDelivery): NotificationDelivery = jpa.save(delivery)
    override fun findByRuleId(ruleId: UUID): List<NotificationDelivery> = jpa.findAllByRuleIdOrderBySentAtDesc(ruleId)
}
