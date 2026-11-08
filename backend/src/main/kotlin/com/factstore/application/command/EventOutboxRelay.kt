package com.factstore.application.command

import com.factstore.application.observability.FailureMetrics
import com.factstore.core.port.outbound.IDomainEventBus
import com.factstore.core.port.outbound.IEventOutbox
import com.factstore.core.port.outbound.IEventStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Re-publishes committed domain events whose post-commit publish did not succeed (#185).
 *
 * Runs on a fixed delay. Entries younger than the grace period are left alone, so the relay does
 * not race the `afterCommit` publish that is normally still in flight for them. Events go out
 * oldest first, and a pass stops at the first failure: if the broker is down the next entry
 * would fail the same way.
 */
@Component
class EventOutboxRelay(
    private val outbox: IEventOutbox,
    private val eventStore: IEventStore,
    private val domainEventBus: IDomainEventBus,
    private val failureMetrics: FailureMetrics,
    @Value("\${factstore.events.outbox.grace-seconds:30}") private val graceSeconds: Long,
    @Value("\${factstore.events.outbox.batch-size:100}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(EventOutboxRelay::class.java)

    @Scheduled(
        fixedDelayString = "\${factstore.events.outbox.relay-interval-ms:10000}",
        initialDelayString = "\${factstore.events.outbox.relay-interval-ms:10000}"
    )
    fun tick() {
        relayPending(Instant.now().minus(Duration.ofSeconds(graceSeconds)))
    }

    /** Publish every outstanding event enqueued before [enqueuedBefore]; returns how many went out. */
    fun relayPending(enqueuedBefore: Instant): Int {
        val sequenceNumbers = outbox.pending(enqueuedBefore, batchSize)
        if (sequenceNumbers.isEmpty()) return 0

        val entries = eventStore.findBySequenceNumbers(sequenceNumbers).associateBy { it.sequenceNumber }
        var published = 0
        for (seq in sequenceNumbers) {
            val entry = entries[seq]
            if (entry == null) {
                log.warn("Outbox refers to seq={} which is not in the event store; discarding the entry", seq)
                outbox.remove(seq)
                continue
            }
            try {
                domainEventBus.publish(entry)
            } catch (e: Exception) {
                failureMetrics.eventPublishFailure()
                outbox.recordFailure(seq, e.message ?: e::class.java.name)
                log.warn("Relay could not publish seq={} type={}; retrying on the next pass: {}",
                    seq, entry.eventType, e.message)
                break
            }
            outbox.remove(seq)
            published++
        }
        if (published > 0) log.info("Relayed {} outstanding domain event(s) from the outbox", published)
        return published
    }
}
