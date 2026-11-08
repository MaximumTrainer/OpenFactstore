package com.factstore.observability

import com.factstore.adapter.inbound.messaging.RabbitMqEventConsumer
import com.factstore.adapter.mock.InMemoryNotificationDeliveryRepository
import com.factstore.adapter.mock.InMemoryNotificationRuleRepository
import com.factstore.application.LedgerService
import com.factstore.application.NotificationDispatchService
import com.factstore.application.ReadModelProjector
import com.factstore.application.observability.FailureMetrics
import com.factstore.application.observability.ProjectionFailureRecorder
import com.factstore.config.LedgerProperties
import com.factstore.core.domain.ChainVerificationResult
import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.LedgerEntry
import com.factstore.core.domain.LedgerReceipt
import com.factstore.core.domain.LedgerRecord
import com.factstore.core.domain.NotificationDeliveryStatus
import com.factstore.core.domain.NotificationRule
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.domain.VerificationResult
import com.factstore.core.port.outbound.IImmutableLedger
import com.factstore.core.port.outbound.INotificationChannel
import com.factstore.core.port.outbound.IProjectionFailureRepository
import com.factstore.dto.NotificationEvent
import com.factstore.dto.VerifyChainRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * #185 — failures that used to be logged and dropped are now recorded and counted, so an alert
 * can fire on them and an operator can recover.
 */
@SpringBootTest
@Transactional
class SilentFailureVisibilityTest {

    @Autowired lateinit var projector: ReadModelProjector
    @Autowired lateinit var recorder: ProjectionFailureRecorder
    @Autowired lateinit var projectionFailures: IProjectionFailureRepository
    @Autowired lateinit var meterRegistry: MeterRegistry

    private fun counter(name: String, vararg tags: String) =
        meterRegistry.find(name).tags(*tags).counter()?.count() ?: 0.0

    private fun entry(eventType: String, payload: String) = EventLogEntry(
        sequenceNumber = System.nanoTime(),
        aggregateId = UUID.randomUUID(),
        aggregateType = "Flow",
        eventType = eventType,
        payload = payload
    )

    // ── Projection failures ────────────────────────────────────────────────────

    @Test
    fun `a malformed payload is recorded as a projection failure instead of being acknowledged as success`() {
        val consumer = RabbitMqEventConsumer(projector, recorder)
        val before = counter("factstore.projection.failures")
        val malformed = entry("FlowCreated", "{not json")

        consumer.onEvent(malformed)

        val recorded = projectionFailures.findBySequenceNumber(malformed.sequenceNumber)
        assertThat(recorded).isNotNull
        assertThat(recorded!!.eventType).isEqualTo("FlowCreated")
        assertThat(recorded.payload).isEqualTo("{not json")
        assertThat(counter("factstore.projection.failures")).isEqualTo(before + 1)
    }

    @Test
    fun `an unknown event type is recorded as a projection failure`() {
        val consumer = RabbitMqEventConsumer(projector, recorder)
        val unknown = entry("SomethingNobodyDefined", "{}")

        consumer.onEvent(unknown)

        assertThat(projectionFailures.findBySequenceNumber(unknown.sequenceNumber)).isNotNull
    }

    @Test
    fun `a projection failure counts as an integrity failure from the projection`() {
        val consumer = RabbitMqEventConsumer(projector, recorder)
        val before = counter("factstore.integrity.failures", "source", "projection")

        consumer.onEvent(entry("FlowCreated", "{not json"))

        assertThat(counter("factstore.integrity.failures", "source", "projection")).isEqualTo(before + 1)
    }

    // ── Notification dead letters ──────────────────────────────────────────────

    @Test
    fun `a dead-lettered notification is counted by channel`() {
        val registry = SimpleMeterRegistry()
        val rules = InMemoryNotificationRuleRepository()
        val deliveries = InMemoryNotificationDeliveryRepository()
        val rule = rules.save(NotificationRule(name = "r", triggerEvent = TriggerEvent.GATE_BLOCKED, channelType = ChannelType.WEBHOOK))
        val alwaysFails = object : INotificationChannel {
            override val channelType = ChannelType.WEBHOOK
            override fun send(rule: NotificationRule, event: NotificationEvent) = throw RuntimeException("down")
        }
        val service = NotificationDispatchService(rules, deliveries, listOf(alwaysFails), ObjectMapper(),
            FailureMetrics(registry, "all"), 0L)

        service.dispatch(NotificationEvent(triggerEvent = TriggerEvent.GATE_BLOCKED, title = "t", message = "m"))

        assertThat(deliveries.findByRuleId(rule.id).single().status).isEqualTo(NotificationDeliveryStatus.FAILED)
        assertThat(registry.find("factstore.notification.dead.letters").tags("channel", "WEBHOOK").counter()?.count())
            .isEqualTo(1.0)
    }

    // ── Ledger verification ────────────────────────────────────────────────────

    @Test
    fun `a broken ledger chain counts as an integrity failure from the ledger`() {
        val registry = SimpleMeterRegistry()
        val brokenLedger = object : IImmutableLedger {
            override fun recordFact(fact: LedgerRecord): LedgerReceipt = throw UnsupportedOperationException()
            override fun verifyFact(recordId: UUID): VerificationResult = throw UnsupportedOperationException()
            override fun getHistory(recordId: UUID): List<LedgerEntry> = emptyList()
            override fun listEntries(offset: Int, limit: Int): List<LedgerEntry> = emptyList()
            override fun countEntries(): Long = 2
            override fun verifyChainIntegrity(from: Instant, to: Instant) =
                ChainVerificationResult(false, 2, null, null, "entry-2", "hash mismatch")
        }
        val service = LedgerService(brokenLedger, LedgerProperties(enabled = true, type = "local"), FailureMetrics(registry, "all"))

        service.verifyChain(VerifyChainRequest(from = Instant.EPOCH, to = Instant.now()))

        assertThat(registry.find("factstore.integrity.failures").tags("source", "ledger").counter()?.count())
            .isEqualTo(1.0)
    }
}
