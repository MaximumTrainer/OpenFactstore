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
}
