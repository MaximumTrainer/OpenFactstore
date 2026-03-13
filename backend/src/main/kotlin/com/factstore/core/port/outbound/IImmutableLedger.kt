package com.factstore.core.port.outbound

import com.factstore.core.domain.ChainVerificationResult
import com.factstore.core.domain.LedgerEntry
import com.factstore.core.domain.LedgerFact
import com.factstore.core.domain.LedgerReceipt
import com.factstore.core.domain.VerificationResult
import java.time.Instant
import java.util.UUID

interface IImmutableLedger {
    fun recordFact(fact: LedgerFact): LedgerReceipt
    fun verifyFact(factId: UUID): VerificationResult
    fun getHistory(factId: UUID): List<LedgerEntry>
    fun verifyChainIntegrity(from: Instant, to: Instant): ChainVerificationResult
    fun listEntries(offset: Int, limit: Int): List<LedgerEntry>
    fun countEntries(): Long
}
