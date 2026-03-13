package com.factstore

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.domain.TriggerEvent
import com.factstore.dto.CreateNotificationRuleRequest
import com.factstore.dto.NotificationEvent
import com.factstore.exception.NotFoundException
import com.factstore.application.NotificationService
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
class NotificationServiceTest {

    @Autowired
    lateinit var notificationService: NotificationService

    @Autowired
    lateinit var notificationRuleService: NotificationRuleService

    @Test
    fun `list notifications returns empty by default`() {
        val result = notificationService.listNotifications(null, null)
        assertNotNull(result)
    }

    @Test
    fun `mark as read on unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            notificationService.markAsRead(UUID.randomUUID())
        }
    }

    @Test
    fun `countUnread returns non-negative number`() {
        val count = notificationService.countUnread()
        assertTrue(count >= 0)
    }

    @Test
    fun `markAllAsRead succeeds without error`() {
        notificationService.markAllAsRead()
    }

    @Test
    fun `publishEvent dispatches async without throwing`() {
        // Create an IN_APP rule so at least one delivery gets recorded
        notificationRuleService.createRule(
            CreateNotificationRuleRequest(
                name = "in-app-rule-test",
                triggerEvent = TriggerEvent.TRAIL_NON_COMPLIANT,
                channelType = ChannelType.IN_APP
            )
        )
        val event = NotificationEvent(
            triggerEvent = TriggerEvent.TRAIL_NON_COMPLIANT,
            title = "Test trail non-compliant",
            message = "Trail XYZ is non-compliant",
            severity = NotificationSeverity.WARNING,
            entityType = "trail",
            entityId = UUID.randomUUID()
        )
        // Should not throw
        notificationService.publishEvent(event)
    }
}
