package com.factstore.core.domain

import java.time.Instant
import java.util.UUID

data class LedgerFact(
    val factId: UUID,
    val eventType: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap()
)

data class LedgerEntry(
    val entryId: String,
    val factId: UUID,
    val eventType: String,
    val contentHash: String,
    val previousHash: String,
    val timestamp: Instant,
    val metadata: Map<String, String>
)

data class LedgerReceipt(
    val entryId: String,
    val factId: UUID,
    val contentHash: String,
    val timestamp: Instant
)

data class VerificationResult(
    val factId: UUID,
    val verified: Boolean,
    val contentHash: String?,
    val chainPosition: Int?,
    val previousHash: String?,
    val ledgerTimestamp: Instant?,
    val verifiedAt: Instant,
    val message: String
)

data class ChainVerificationResult(
    val valid: Boolean,
    val entriesChecked: Int,
    val firstEntryTimestamp: Instant?,
    val lastEntryTimestamp: Instant?,
    val brokenAt: String?,
    val message: String
)
