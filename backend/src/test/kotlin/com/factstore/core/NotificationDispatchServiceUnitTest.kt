package com.factstore.core

import com.factstore.adapter.mock.InMemoryNotificationDeliveryRepository
import com.factstore.adapter.mock.InMemoryNotificationRuleRepository
import com.factstore.application.NotificationDispatchService
import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationDeliveryStatus
import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.port.outbound.INotificationChannel
import com.factstore.dto.NotificationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for NotificationDispatchService retry/backoff and dead-letter logic.
 * Runs without a Spring context using in-memory adapters.
 */
class NotificationDispatchServiceUnitTest {

    private lateinit var ruleRepository: InMemoryNotificationRuleRepository
    private lateinit var deliveryRepository: InMemoryNotificationDeliveryRepository
    private lateinit var dispatchService: NotificationDispatchService

    private val event = NotificationEvent(
        triggerEvent = TriggerEvent.TRAIL_NON_COMPLIANT,
        title = "Test Title",
        message = "Test Message",
        severity = NotificationSeverity.WARNING
    )

    @BeforeEach
    fun setUp() {
        ruleRepository = InMemoryNotificationRuleRepository()
        deliveryRepository = InMemoryNotificationDeliveryRepository()
    }

    private fun buildDispatchService(channel: INotificationChannel): NotificationDispatchService =
        // Use baseBackoffSeconds=0 for instantaneous retries in tests
        NotificationDispatchService(ruleRepository, deliveryRepository, listOf(channel), ObjectMapper(), 0L)

    private fun createRule(channelType: ChannelType = ChannelType.IN_APP): NotificationRule {
        val rule = NotificationRule(
            name = "test-rule",
            triggerEvent = TriggerEvent.TRAIL_NON_COMPLIANT,
            channelType = channelType
        )
        ruleRepository.save(rule)
        return rule
    }

    @Test
    fun `dispatch succeeds on first attempt - records SENT delivery with attemptCount 1`() {
        val rule = createRule()
        var callCount = 0
        val channel = object : INotificationChannel {
            override val channelType = ChannelType.IN_APP
            override fun send(rule: NotificationRule, event: NotificationEvent) { callCount++ }
        }
        dispatchService = buildDispatchService(channel)

        dispatchService.dispatch(event)

        assertEquals(1, callCount)
        val deliveries = deliveryRepository.findByRuleId(rule.id)
        assertEquals(1, deliveries.size)
        assertEquals(NotificationDeliveryStatus.SENT, deliveries[0].status)
        assertEquals(1, deliveries[0].attemptCount)
        assertNull(deliveries[0].error)
    }

    @Test
    fun `dispatch succeeds on second attempt after first failure - records SENT with attemptCount 2`() {
        val rule = createRule()
        var callCount = 0
        val channel = object : INotificationChannel {
            override val channelType = ChannelType.IN_APP
            override fun send(rule: NotificationRule, event: NotificationEvent) {
                callCount++
                if (callCount == 1) throw RuntimeException("transient failure")
            }
        }
        dispatchService = buildDispatchService(channel)

        dispatchService.dispatch(event)

        assertEquals(2, callCount)
        val deliveries = deliveryRepository.findByRuleId(rule.id)
        assertEquals(1, deliveries.size)
        assertEquals(NotificationDeliveryStatus.SENT, deliveries[0].status)
        assertEquals(2, deliveries[0].attemptCount)
    }

    @Test
    fun `dispatch fails all 3 attempts - records FAILED delivery with attemptCount 3 and error message`() {
        val rule = createRule()
        var callCount = 0
        val channel = object : INotificationChannel {
            override val channelType = ChannelType.IN_APP
            override fun send(rule: NotificationRule, event: NotificationEvent) {
                callCount++
                throw RuntimeException("persistent error")
            }
        }
        dispatchService = buildDispatchService(channel)

        dispatchService.dispatch(event)

        assertEquals(3, callCount)
        val deliveries = deliveryRepository.findByRuleId(rule.id)
        assertEquals(1, deliveries.size)
        assertEquals(NotificationDeliveryStatus.FAILED, deliveries[0].status)
        assertEquals(3, deliveries[0].attemptCount)
        assertEquals("persistent error", deliveries[0].error)
    }

    @Test
    fun `dispatch with no matching channel type - records SKIPPED delivery`() {
        createRule(ChannelType.SLACK)
        // Supply only an IN_APP channel so SLACK has no handler
        val inAppChannel = object : INotificationChannel {
            override val channelType = ChannelType.IN_APP
            override fun send(rule: NotificationRule, event: NotificationEvent) {}
        }
        dispatchService = buildDispatchService(inAppChannel)

        dispatchService.dispatch(event)

        val deliveries = deliveryRepository.findByRuleId(
            ruleRepository.findAll().first().id
        )
        assertEquals(1, deliveries.size)
        assertEquals(NotificationDeliveryStatus.SKIPPED, deliveries[0].status)
        assertEquals(0, deliveries[0].attemptCount)
    }

    @Test
    fun `dispatch skips rule when filterFlowId does not match event filterFlowId`() {
        val rule = createRule()
        val filteredRule = NotificationRule(
            name = "filtered-rule",
            triggerEvent = TriggerEvent.TRAIL_NON_COMPLIANT,
            channelType = ChannelType.IN_APP,
            filterFlowId = java.util.UUID.randomUUID() // different from event's filterFlowId (null)
        )
        ruleRepository.save(filteredRule)

        var callCount = 0
        val channel = object : INotificationChannel {
            override val channelType = ChannelType.IN_APP
            override fun send(r: NotificationRule, e: NotificationEvent) {
                callCount++
            }
        }
        dispatchService = buildDispatchService(channel)

        dispatchService.dispatch(event) // event has filterFlowId = null

        // Only the unfiltered rule should fire
        assertEquals(1, callCount)
        val allDeliveries = deliveryRepository.findByRuleId(rule.id) +
                deliveryRepository.findByRuleId(filteredRule.id)
        assertEquals(1, allDeliveries.size)
        assertEquals(rule.id, allDeliveries[0].ruleId)
    }
}
