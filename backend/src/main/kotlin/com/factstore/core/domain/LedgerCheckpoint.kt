package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A signed Merkle checkpoint over a contiguous range of signed event records (#188). Immutable:
 * the next checkpoint starts after [lastSequence].
 */
@Entity
@Table(name = "ledger_checkpoints")
class LedgerCheckpoint(
    @Id
    @Column(name = "checkpoint_index")
    val checkpointIndex: Long,

    @Column(name = "first_sequence", nullable = false)
    val firstSequence: Long,

    @Column(name = "last_sequence", nullable = false)
    val lastSequence: Long,

    @Column(name = "leaf_count", nullable = false)
    val leafCount: Int,

    /** RFC 9162 Merkle tree hash over the records' `record_hash` values, lower-case hex. */
    @Column(name = "root_hash", nullable = false, length = 64)
    val rootHash: String,

    /** The exact signed statement: RFC 8785 JSON naming the index, range, leaf count and root. */
    @Column(name = "statement", nullable = false, columnDefinition = "TEXT")
    val statement: String,

    @Column(name = "signature", nullable = false, columnDefinition = "TEXT")
    val signature: String,

    @Column(name = "signing_key_id", nullable = false, length = 128)
    val signingKeyId: String,

    @Column(name = "signature_algorithm", nullable = false, length = 32)
    val signatureAlgorithm: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
