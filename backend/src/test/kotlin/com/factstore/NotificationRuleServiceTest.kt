package com.factstore

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.TriggerEvent
import com.factstore.dto.CreateNotificationRuleRequest
import com.factstore.dto.UpdateNotificationRuleRequest
import com.factstore.exception.NotFoundException
import com.factstore.application.NotificationRuleService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class NotificationRuleServiceTest {

    @Autowired
    lateinit var notificationRuleService: NotificationRuleService

    @Test
    fun `create rule succeeds`() {
        val req = CreateNotificationRuleRequest(
            name = "test-rule",
            triggerEvent = TriggerEvent.ATTESTATION_FAILED,
            channelType = ChannelType.IN_APP,
            channelConfig = "{}"
        )
        val resp = notificationRuleService.createRule(req)
        assertNotNull(resp.id)
        assertEquals("test-rule", resp.name)
        assertEquals(TriggerEvent.ATTESTATION_FAILED, resp.triggerEvent)
        assertEquals(ChannelType.IN_APP, resp.channelType)
        assertTrue(resp.isActive)
    }

    @Test
    fun `list rules returns all created rules`() {
        notificationRuleService.createRule(
            CreateNotificationRuleRequest("rule-a", TriggerEvent.TRAIL_NON_COMPLIANT, ChannelType.IN_APP)
        )
        notificationRuleService.createRule(
            CreateNotificationRuleRequest("rule-b", TriggerEvent.GATE_BLOCKED, ChannelType.IN_APP)
        )
        val rules = notificationRuleService.listRules()
        assertTrue(rules.size >= 2)
    }

    @Test
    fun `get rule by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            notificationRuleService.getRule(UUID.randomUUID())
        }
    }

    @Test
    fun `update rule updates fields`() {
        val created = notificationRuleService.createRule(
            CreateNotificationRuleRequest("orig-rule", TriggerEvent.TRAIL_NON_COMPLIANT, ChannelType.IN_APP)
        )
        val updated = notificationRuleService.updateRule(
            created.id,
            UpdateNotificationRuleRequest(name = "updated-rule", isActive = false)
        )
        assertEquals("updated-rule", updated.name)
        assertFalse(updated.isActive)
    }

    @Test
    fun `delete rule removes it`() {
        val created = notificationRuleService.createRule(
            CreateNotificationRuleRequest("del-rule", TriggerEvent.APPROVAL_REQUIRED, ChannelType.IN_APP)
        )
        notificationRuleService.deleteRule(created.id)
        assertThrows<NotFoundException> {
            notificationRuleService.getRule(created.id)
        }
    }

    @Test
    fun `delete rule with unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            notificationRuleService.deleteRule(UUID.randomUUID())
        }
    }

    @Test
    fun `getRuleDeliveries returns empty list for new rule`() {
        val created = notificationRuleService.createRule(
            CreateNotificationRuleRequest("delivery-rule", TriggerEvent.APPROVAL_REJECTED, ChannelType.IN_APP)
        )
        val deliveries = notificationRuleService.getRuleDeliveries(created.id)
        assertTrue(deliveries.isEmpty())
    }
}
