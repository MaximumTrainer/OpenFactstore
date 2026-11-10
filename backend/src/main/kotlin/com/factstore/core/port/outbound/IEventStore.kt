package com.factstore.core.port.outbound

import com.factstore.core.domain.EventLogEntry
import java.util.UUID

/**
 * Append-only event store. The event log is the single source of truth
 * for every state change that has occurred in the system.
 */
interface IEventStore {
    /** Append one event to the log. */
    fun append(entry: EventLogEntry): EventLogEntry

    /** Retrieve all events for a given aggregate, ordered by sequence number. */
    fun findByAggregateId(aggregateId: UUID): List<EventLogEntry>

    /** Retrieve all events of a given aggregate type, ordered by sequence number. */
    fun findByAggregateType(aggregateType: String): List<EventLogEntry>

    /** Stream every event in the log, ordered by sequence number (for replay). */
    fun findAll(): List<EventLogEntry>

    /** Stream events whose sequence number is greater than [afterSequence] (for incremental replay). */
    fun findAfterSequence(afterSequence: Long): List<EventLogEntry>

    /** Retrieve the events with the given sequence numbers, ordered by sequence number. */
    fun findBySequenceNumbers(sequenceNumbers: Collection<Long>): List<EventLogEntry>

    /** Persist the signature fields of an entry appended in the same transaction (#184). */
    fun recordSignature(entry: EventLogEntry): EventLogEntry

    /** Signed entries with sequence numbers in [from, to], in order (#184). */
    fun findSignedInRange(from: Long, to: Long): List<EventLogEntry>

    /** The newest signed entry before [sequenceNumber], if any (#184). */
    fun findLastSignedBefore(sequenceNumber: Long): EventLogEntry?

    /** How many unsigned entries have sequence numbers in [from, to] (#184). */
    fun countUnsignedInRange(from: Long, to: Long): Long
}
