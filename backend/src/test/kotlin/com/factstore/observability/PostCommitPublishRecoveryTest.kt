package com.factstore.observability

import com.factstore.application.command.EventOutboxRelay
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.inbound.command.IFlowCommandHandler
import com.factstore.core.port.outbound.IDomainEventBus
import com.factstore.core.port.outbound.IEventOutbox
import com.factstore.dto.command.CreateFlowCommand
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * #185 — a failed post-commit publish must not be dropped.
 *
 * The fact is committed to the event store before it is published, so a broker outage used to
 * mean the query side silently never heard about it ("replay from the event store to recover",
 * said the log, and nothing did). Every committed event is now recorded in an outbox in the same
 * transaction, and a relay re-publishes whatever is still outstanding once the broker is back.
 *
 * Deliberately not @Transactional: the publish happens after commit, so the test must commit.
 */
@SpringBootTest
@Import(PostCommitPublishRecoveryTest.BrokerStandIn::class)
class PostCommitPublishRecoveryTest {

    @Autowired lateinit var flowCommands: IFlowCommandHandler
    @Autowired lateinit var broker: SwitchableBroker
    @Autowired lateinit var outbox: IEventOutbox
    @Autowired lateinit var relay: EventOutboxRelay
    @Autowired lateinit var meterRegistry: MeterRegistry

    /** A broker that can be "stopped" and "restarted", recording what it delivered. */
    class SwitchableBroker : IDomainEventBus {
        @Volatile var up = true
        val delivered = CopyOnWriteArrayList<EventLogEntry>()
        override fun publish(entry: EventLogEntry) {
            check(up) { "broker unreachable" }
            delivered += entry
        }
    }

    @TestConfiguration
    class BrokerStandIn {
        @Bean @Primary fun switchableBroker() = SwitchableBroker()
    }

    private fun publishFailures() =
        meterRegistry.find("factstore.event.publish.failures").counter()?.count() ?: 0.0

    @BeforeEach
    fun reset() {
        broker.up = true
        broker.delivered.clear()
        relay.relayPending(Instant.now().plusSeconds(3600))
        broker.delivered.clear()
    }

    @Test
    fun `a successful publish leaves nothing outstanding in the outbox`() {
        val result = flowCommands.createFlow(CreateFlowCommand(name = "outbox-ok-${System.nanoTime()}"))

        assertThat(broker.delivered.map { it.aggregateId }).contains(result.id)
        assertThat(outbox.pending(Instant.now().plusSeconds(3600), 100)).isEmpty()
    }

    @Test
    fun `a fact committed while the broker is down reaches the query side once it is back`() {
        val failuresBefore = publishFailures()

        // Given the broker is down
        broker.up = false
        // When a fact is committed
        val result = flowCommands.createFlow(CreateFlowCommand(name = "outbox-down-${System.nanoTime()}"))

        // Then the publish failure is counted and the event is held, not dropped
        assertThat(publishFailures()).isEqualTo(failuresBefore + 1)
        assertThat(broker.delivered).isEmpty()
        assertThat(outbox.pending(Instant.now().plusSeconds(3600), 100)).hasSize(1)

        // When the broker comes back and the relay runs its next tick
        broker.up = true
        val relayed = relay.relayPending(Instant.now().plusSeconds(3600))

        // Then the query side receives the event, with no manual step, and nothing is outstanding
        assertThat(relayed).isEqualTo(1)
        assertThat(broker.delivered.map { it.aggregateId }).containsExactly(result.id)
        assertThat(outbox.pending(Instant.now().plusSeconds(3600), 100)).isEmpty()
    }

    @Test
    fun `the relay leaves an event outstanding while the broker is still down`() {
        broker.up = false
        flowCommands.createFlow(CreateFlowCommand(name = "outbox-still-down-${System.nanoTime()}"))
        val failuresAfterCommit = publishFailures()

        val relayed = relay.relayPending(Instant.now().plusSeconds(3600))

        assertThat(relayed).isZero()
        assertThat(publishFailures()).isEqualTo(failuresAfterCommit + 1)
        assertThat(outbox.pending(Instant.now().plusSeconds(3600), 100)).hasSize(1)
    }

    @Test
    fun `the relay does not race a publish that is still in flight`() {
        broker.up = false
        flowCommands.createFlow(CreateFlowCommand(name = "outbox-grace-${System.nanoTime()}"))
        broker.up = true

        // An entry younger than the cutoff is left for the in-flight afterCommit publish.
        val relayed = relay.relayPending(Instant.now().minusSeconds(60))

        assertThat(relayed).isZero()
        assertThat(broker.delivered).isEmpty()
    }
}
