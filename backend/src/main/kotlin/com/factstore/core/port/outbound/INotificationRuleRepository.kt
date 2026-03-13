package com.factstore.core.port.outbound

import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.TriggerEvent
import java.util.UUID

interface INotificationRuleRepository {
    fun save(rule: NotificationRule): NotificationRule
    fun findById(id: UUID): NotificationRule?
    fun findAll(): List<NotificationRule>
    fun findAllActiveByTriggerEvent(triggerEvent: TriggerEvent): List<NotificationRule>
    fun existsById(id: UUID): Boolean
    fun deleteById(id: UUID)
}
