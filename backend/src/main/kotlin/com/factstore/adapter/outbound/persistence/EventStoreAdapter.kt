package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IEventStore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventLogEntryJpa : JpaRepository<EventLogEntry, Long> {
    fun findByAggregateIdOrderBySequenceNumber(aggregateId: UUID): List<EventLogEntry>
    fun findByAggregateTypeOrderBySequenceNumber(aggregateType: String): List<EventLogEntry>
    fun findAllByOrderBySequenceNumber(): List<EventLogEntry>
    fun findBySequenceNumberGreaterThanOrderBySequenceNumber(sequenceNumber: Long): List<EventLogEntry>
    fun findBySequenceNumberInOrderBySequenceNumber(sequenceNumbers: Collection<Long>): List<EventLogEntry>
    fun findBySignatureStatusAndSequenceNumberBetweenOrderBySequenceNumber(status: String, from: Long, to: Long): List<EventLogEntry>
    fun findFirstBySignatureStatusAndSequenceNumberLessThanOrderBySequenceNumberDesc(status: String, sequenceNumber: Long): EventLogEntry?
    fun countBySignatureStatusAndSequenceNumberBetween(status: String, from: Long, to: Long): Long
}

@Component
class EventStoreAdapter(private val jpa: EventLogEntryJpa) : IEventStore {

    override fun append(entry: EventLogEntry): EventLogEntry = jpa.save(entry)

    override fun findByAggregateId(aggregateId: UUID): List<EventLogEntry> =
        jpa.findByAggregateIdOrderBySequenceNumber(aggregateId)

    override fun findByAggregateType(aggregateType: String): List<EventLogEntry> =
        jpa.findByAggregateTypeOrderBySequenceNumber(aggregateType)

    override fun findAll(): List<EventLogEntry> = jpa.findAllByOrderBySequenceNumber()

    override fun findAfterSequence(afterSequence: Long): List<EventLogEntry> =
        jpa.findBySequenceNumberGreaterThanOrderBySequenceNumber(afterSequence)

    override fun findBySequenceNumbers(sequenceNumbers: Collection<Long>): List<EventLogEntry> =
        if (sequenceNumbers.isEmpty()) emptyList() else jpa.findBySequenceNumberInOrderBySequenceNumber(sequenceNumbers)

    override fun recordSignature(entry: EventLogEntry): EventLogEntry = jpa.save(entry)

    override fun findSignedInRange(from: Long, to: Long): List<EventLogEntry> =
        jpa.findBySignatureStatusAndSequenceNumberBetweenOrderBySequenceNumber(EventLogEntry.SIGNED, from, to)

    override fun findLastSignedBefore(sequenceNumber: Long): EventLogEntry? =
        jpa.findFirstBySignatureStatusAndSequenceNumberLessThanOrderBySequenceNumberDesc(EventLogEntry.SIGNED, sequenceNumber)

    override fun countUnsignedInRange(from: Long, to: Long): Long =
        jpa.countBySignatureStatusAndSequenceNumberBetween(EventLogEntry.UNSIGNED, from, to)
}
