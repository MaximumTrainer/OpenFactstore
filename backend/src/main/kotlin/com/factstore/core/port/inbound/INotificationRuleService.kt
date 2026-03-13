package com.factstore.core.port.inbound

import com.factstore.dto.CreateNotificationRuleRequest
import com.factstore.dto.NotificationDeliveryResponse
import com.factstore.dto.NotificationRuleResponse
import com.factstore.dto.UpdateNotificationRuleRequest
import java.util.UUID

interface INotificationRuleService {
    fun createRule(request: CreateNotificationRuleRequest): NotificationRuleResponse
    fun listRules(): List<NotificationRuleResponse>
    fun getRule(id: UUID): NotificationRuleResponse
    fun updateRule(id: UUID, request: UpdateNotificationRuleRequest): NotificationRuleResponse
    fun deleteRule(id: UUID)
    fun testRule(id: UUID)
    fun getRuleDeliveries(id: UUID): List<NotificationDeliveryResponse>
}
