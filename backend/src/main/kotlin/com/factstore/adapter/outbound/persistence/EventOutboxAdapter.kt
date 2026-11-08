package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.EventOutboxEntry
import com.factstore.core.port.outbound.IEventOutbox
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface EventOutboxJpa : JpaRepository<EventOutboxEntry, Long> {
    fun findByEnqueuedAtBeforeOrderBySequenceNumber(enqueuedAt: Instant, page: PageRequest): List<EventOutboxEntry>
}

/**
 * [remove] and [recordFailure] run in a transaction of their own: they are called from
 * `afterCommit`, where the committed transaction's resources are still bound to the thread, and
 * joining it would silently discard the change.
 */
@Component
class EventOutboxAdapter(private val jpa: EventOutboxJpa) : IEventOutbox {

    override fun enqueue(sequenceNumber: Long) {
        jpa.save(EventOutboxEntry(sequenceNumber = sequenceNumber))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun remove(sequenceNumber: Long) {
        if (jpa.existsById(sequenceNumber)) jpa.deleteById(sequenceNumber)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordFailure(sequenceNumber: Long, error: String) {
        val entry = jpa.findById(sequenceNumber).orElse(null) ?: return
        entry.attempts += 1
        entry.lastError = error.take(1000)
        entry.lastAttemptAt = Instant.now()
        jpa.save(entry)
    }

    override fun pending(enqueuedBefore: Instant, limit: Int): List<Long> =
        jpa.findByEnqueuedAtBeforeOrderBySequenceNumber(enqueuedBefore, PageRequest.of(0, limit))
            .map { it.sequenceNumber }
}
