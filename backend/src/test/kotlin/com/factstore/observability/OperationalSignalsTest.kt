package com.factstore.observability

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationDeliveryStatus
import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.port.outbound.INotificationDeliveryRepository
import com.factstore.core.port.outbound.INotificationRuleRepository
import com.factstore.exception.IntegrityException
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * #185 — system failures become signals an operator can route: counters an alert rule can be
 * written against, and notification triggers an existing rule can send to Slack or a webhook.
 *
 * Not @Transactional: notification dispatch is asynchronous, so the rule must be committed for
 * the dispatch thread to see it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OperationalSignalsTest.FailingEndpoints::class)
class OperationalSignalsTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var meterRegistry: MeterRegistry
    @Autowired lateinit var rules: INotificationRuleRepository
    @Autowired lateinit var deliveries: INotificationDeliveryRepository
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private val createdRules = mutableListOf<UUID>()

    @TestConfiguration
    class FailingEndpoints {
        @RestController
        class Boom {
            @GetMapping("/test-only/signals/boom")
            fun boom(): String = throw IllegalStateException("database connection reset")

            @GetMapping("/test-only/signals/integrity")
            fun integrity(): String = throw IntegrityException("chain broken")
        }
    }

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
        rateLimiter.reset()
        validationCache.clear()
    }

    @AfterEach
    fun tearDown() {
        createdRules.forEach { rules.deleteById(it) }
        rateLimiter.reset()
    }

    private fun inAppRule(trigger: TriggerEvent): NotificationRule =
        rules.save(NotificationRule(name = "ops-$trigger-${System.nanoTime()}", triggerEvent = trigger, channelType = ChannelType.IN_APP))
            .also { createdRules += it.id }

    /** Waits for an explicit condition rather than sleeping a fixed time. */
    private fun awaitDelivery(ruleId: UUID) {
        val deadline = Instant.now().plus(Duration.ofSeconds(10))
        while (deliveries.findByRuleId(ruleId).isEmpty()) {
            check(Instant.now().isBefore(deadline)) { "no delivery for rule $ruleId within 10s" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a rejected credential is counted by reason`() {
        fun count() = meterRegistry.find("factstore.auth.failures").tags("reason", "UNKNOWN").counter()?.count() ?: 0.0
        val before = count()

        mockMvc.get("/api/v1/flows") { header("Authorization", "Bearer fsp_${"0".repeat(64)}") }

        assertThat(count()).isEqualTo(before + 1)
    }

    @Test
    fun `an unexpected server error fires the SYSTEM_ERROR notification trigger`() {
        val rule = inAppRule(TriggerEvent.SYSTEM_ERROR)

        mockMvc.get("/test-only/signals/boom") { header("X-Request-Id", "ops-system-error") }

        awaitDelivery(rule.id)
        val delivery = deliveries.findByRuleId(rule.id).single()
        assertThat(delivery.status).isEqualTo(NotificationDeliveryStatus.SENT)
        assertThat(delivery.payload).contains("ops-system-error")
        // The operator sees the correlation id to look up, not the exception text.
        assertThat(delivery.payload).doesNotContain("database connection reset")
    }

    @Test
    fun `an integrity failure fires the INTEGRITY_FAILURE notification trigger`() {
        val rule = inAppRule(TriggerEvent.INTEGRITY_FAILURE)

        mockMvc.get("/test-only/signals/integrity") { header("X-Request-Id", "ops-integrity") }

        awaitDelivery(rule.id)
        assertThat(deliveries.findByRuleId(rule.id).single().payload).contains("ops-integrity")
    }
}
