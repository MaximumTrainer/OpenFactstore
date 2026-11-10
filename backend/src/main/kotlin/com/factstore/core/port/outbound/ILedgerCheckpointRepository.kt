package com.factstore.core.port.outbound

import com.factstore.core.domain.LedgerCheckpoint

interface ILedgerCheckpointRepository {
    fun save(checkpoint: LedgerCheckpoint): LedgerCheckpoint
    fun findLatest(): LedgerCheckpoint?
    fun findCovering(sequenceNumber: Long): LedgerCheckpoint?
    fun findAll(): List<LedgerCheckpoint>
}

/** Publishes a signed checkpoint outside the application, where Factstore cannot rewrite it (#188). */
interface ICheckpointPublisher {
    fun publish(checkpoint: LedgerCheckpoint)
}
