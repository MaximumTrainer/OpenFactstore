package com.factstore.core.port.inbound

import com.factstore.dto.ChainVerificationResponse
import com.factstore.dto.LedgerEntryResponse
import com.factstore.dto.LedgerStatusResponse
import com.factstore.dto.PagedLedgerEntriesResponse
import com.factstore.dto.VerifyChainRequest
import com.factstore.dto.VerificationResponse
import java.util.UUID

interface ILedgerService {
    fun listEntries(page: Int, size: Int): PagedLedgerEntriesResponse
    fun getEntry(factId: UUID): LedgerEntryResponse
    fun verifyFact(factId: UUID): VerificationResponse
    fun verifyChain(request: VerifyChainRequest): ChainVerificationResponse
    fun getStatus(): LedgerStatusResponse
}
