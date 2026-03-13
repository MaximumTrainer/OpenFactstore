package com.factstore.adapter.mock

import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.port.outbound.INotificationRuleRepository
import java.util.UUID

class InMemoryNotificationRuleRepository : INotificationRuleRepository {
    private val store = mutableMapOf<UUID, NotificationRule>()

    override fun save(rule: NotificationRule): NotificationRule {
        store[rule.id] = rule
        return rule
    }

    override fun findById(id: UUID): NotificationRule? = store[id]

    override fun findAll(): List<NotificationRule> = store.values.toList()

    override fun findAllActiveByTriggerEvent(triggerEvent: TriggerEvent): List<NotificationRule> =
        store.values.filter { it.isActive && it.triggerEvent == triggerEvent }

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun deleteById(id: UUID) {
        store.remove(id)
    }
}
