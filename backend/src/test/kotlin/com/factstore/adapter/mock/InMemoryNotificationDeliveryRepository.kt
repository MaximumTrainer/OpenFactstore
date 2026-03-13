package com.factstore.adapter.mock

import com.factstore.core.domain.NotificationDelivery
import com.factstore.core.port.outbound.INotificationDeliveryRepository
import java.util.UUID

class InMemoryNotificationDeliveryRepository : INotificationDeliveryRepository {
    private val store = mutableListOf<NotificationDelivery>()

    override fun save(delivery: NotificationDelivery): NotificationDelivery {
        store.add(delivery)
        return delivery
    }

    override fun findByRuleId(ruleId: UUID): List<NotificationDelivery> =
        store.filter { it.ruleId == ruleId }.sortedByDescending { it.sentAt }
}
