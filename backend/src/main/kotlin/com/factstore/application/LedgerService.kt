package com.factstore.application

import com.factstore.config.LedgerProperties
import com.factstore.core.port.inbound.ILedgerService
import com.factstore.core.port.outbound.IImmutableLedger
import com.factstore.dto.ChainVerificationResponse
import com.factstore.dto.LedgerEntryResponse
import com.factstore.dto.LedgerStatusResponse
import com.factstore.dto.PagedLedgerEntriesResponse
import com.factstore.dto.VerificationResponse
import com.factstore.dto.VerifyChainRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.ceil

@Service
@ConditionalOnProperty(name = ["ledger.enabled"], havingValue = "true")
class LedgerService(
    private val ledger: IImmutableLedger,
    private val properties: LedgerProperties
) : ILedgerService {

    private val log = LoggerFactory.getLogger(LedgerService::class.java)

    override fun listEntries(page: Int, size: Int): PagedLedgerEntriesResponse {
        if (page < 0) throw BadRequestException("page must be >= 0")
        if (size <= 0) throw BadRequestException("size must be > 0")
        val offset = page * size
        val entries = ledger.listEntries(offset, size)
        val total = ledger.countEntries()
        val totalPages = if (total == 0L) 0 else ceil(total.toDouble() / size).toInt()
        return PagedLedgerEntriesResponse(
            entries = entries.map { it.toResponse() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages
        )
    }

    override fun getEntry(factId: UUID): LedgerEntryResponse {
        val history = ledger.getHistory(factId)
        return history.lastOrNull()?.toResponse()
            ?: throw NotFoundException("No ledger entry found for factId: $factId")
    }

    override fun verifyFact(factId: UUID): VerificationResponse {
        log.info("Verifying ledger fact factId={}", factId)
        val result = ledger.verifyFact(factId)
        return VerificationResponse(
            factId = result.factId,
            verified = result.verified,
            contentHash = result.contentHash,
            chainPosition = result.chainPosition,
            previousHash = result.previousHash,
            ledgerTimestamp = result.ledgerTimestamp,
            verifiedAt = result.verifiedAt,
            message = result.message
        )
    }

    override fun verifyChain(request: VerifyChainRequest): ChainVerificationResponse {
        log.info("Verifying chain integrity from={} to={}", request.from, request.to)
        val result = ledger.verifyChainIntegrity(request.from, request.to)
        return ChainVerificationResponse(
            valid = result.valid,
            entriesChecked = result.entriesChecked,
            firstEntryTimestamp = result.firstEntryTimestamp,
            lastEntryTimestamp = result.lastEntryTimestamp,
            brokenAt = result.brokenAt,
            message = result.message
        )
    }

    override fun getStatus(): LedgerStatusResponse {
        val total = ledger.countEntries()
        return LedgerStatusResponse(
            enabled = properties.enabled,
            type = properties.type,
            totalEntries = total,
            healthy = true,
            message = "Ledger is operational (type=${properties.type}, entries=$total)"
        )
    }

    private fun com.factstore.core.domain.LedgerEntry.toResponse() = LedgerEntryResponse(
        entryId = entryId,
        factId = factId,
        eventType = eventType,
        contentHash = contentHash,
        previousHash = previousHash,
        timestamp = timestamp,
        metadata = metadata
    )
}
