package com.factstore.core.port.outbound

import com.factstore.core.domain.NotificationDelivery
import java.util.UUID

interface INotificationDeliveryRepository {
    fun save(delivery: NotificationDelivery): NotificationDelivery
    fun findByRuleId(ruleId: UUID): List<NotificationDelivery>
}
