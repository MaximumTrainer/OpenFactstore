package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.port.outbound.INotificationRuleRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationRuleRepositoryJpa : JpaRepository<NotificationRule, UUID> {
    fun findAllByIsActiveTrueAndTriggerEvent(triggerEvent: TriggerEvent): List<NotificationRule>
}

@Component
class NotificationRuleRepositoryAdapter(private val jpa: NotificationRuleRepositoryJpa) : INotificationRuleRepository {
    override fun save(rule: NotificationRule): NotificationRule = jpa.save(rule)
    override fun findById(id: UUID): NotificationRule? = jpa.findById(id).orElse(null)
    override fun findAll(): List<NotificationRule> = jpa.findAll()
    override fun findAllActiveByTriggerEvent(triggerEvent: TriggerEvent): List<NotificationRule> =
        jpa.findAllByIsActiveTrueAndTriggerEvent(triggerEvent)
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
