package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.LedgerCheckpoint
import com.factstore.core.port.outbound.ILedgerCheckpointRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@Repository
interface LedgerCheckpointJpa : JpaRepository<LedgerCheckpoint, Long> {
    fun findFirstByOrderByCheckpointIndexDesc(): LedgerCheckpoint?
    fun findFirstByFirstSequenceLessThanEqualAndLastSequenceGreaterThanEqual(a: Long, b: Long): LedgerCheckpoint?
    fun findAllByOrderByCheckpointIndex(): List<LedgerCheckpoint>
}

@Component
class LedgerCheckpointRepositoryAdapter(private val jpa: LedgerCheckpointJpa) : ILedgerCheckpointRepository {
    override fun save(checkpoint: LedgerCheckpoint): LedgerCheckpoint = jpa.save(checkpoint)
    override fun findLatest(): LedgerCheckpoint? = jpa.findFirstByOrderByCheckpointIndexDesc()
    override fun findCovering(sequenceNumber: Long): LedgerCheckpoint? =
        jpa.findFirstByFirstSequenceLessThanEqualAndLastSequenceGreaterThanEqual(sequenceNumber, sequenceNumber)
    override fun findAll(): List<LedgerCheckpoint> = jpa.findAllByOrderByCheckpointIndex()
}
