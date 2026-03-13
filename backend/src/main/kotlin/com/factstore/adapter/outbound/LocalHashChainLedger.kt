package com.factstore.adapter.outbound

import com.factstore.config.LedgerProperties
import com.factstore.core.domain.ChainVerificationResult
import com.factstore.core.domain.LedgerEntry
import com.factstore.core.domain.LedgerFact
import com.factstore.core.domain.LedgerReceipt
import com.factstore.core.domain.VerificationResult
import com.factstore.core.port.outbound.IImmutableLedger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Component
@ConditionalOnExpression("\${ledger.enabled:false} and '\${ledger.type:local}' == 'local'")
class LocalHashChainLedger(properties: LedgerProperties) : IImmutableLedger {

    private val log = LoggerFactory.getLogger(LocalHashChainLedger::class.java)

    private val entries: CopyOnWriteArrayList<LedgerEntry> = CopyOnWriteArrayList()
    private val appendLock = Any()

    companion object {
        private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }

    init {
        log.info(
            "Local in-memory hash-chain ledger initialised (note: data is not persisted; storage-path={} is unused in this adapter)",
            properties.local.storagePath
        )
    }

    override fun recordFact(fact: LedgerFact): LedgerReceipt {
        // Synchronize the read-compute-append sequence to maintain a strictly sequential chain.
        // CopyOnWriteArrayList guarantees safe concurrent reads, but the "read tail → compute
        // previousHash → append" operation must be atomic to prevent two concurrent writers
        // from each observing the same last entry and generating duplicate previousHash values.
        synchronized(appendLock) {
            val contentHash = sha256(fact.content)
            val previousHash = if (entries.isEmpty()) GENESIS_HASH else computeEntryHash(entries.last())
            val entryId = UUID.randomUUID().toString()
            val timestamp = Instant.now()

            val entry = LedgerEntry(
                entryId = entryId,
                factId = fact.factId,
                eventType = fact.eventType,
                contentHash = contentHash,
                previousHash = previousHash,
                timestamp = timestamp,
                metadata = fact.metadata
            )
            entries.add(entry)
            log.debug("Recorded ledger entry id={} factId={} position={}", entryId, fact.factId, entries.size - 1)

            return LedgerReceipt(
                entryId = entryId,
                factId = fact.factId,
                contentHash = contentHash,
                timestamp = timestamp
            )
        }
    }

    override fun verifyFact(factId: UUID): VerificationResult {
        val snapshot = entries.toList()
        val index = snapshot.indexOfFirst { it.factId == factId }
        if (index < 0) {
            return VerificationResult(
                factId = factId,
                verified = false,
                contentHash = null,
                chainPosition = null,
                previousHash = null,
                ledgerTimestamp = null,
                verifiedAt = Instant.now(),
                message = "No ledger entry found for factId $factId"
            )
        }
        val entry = snapshot[index]
        val chainValid = isChainValidAt(snapshot, index)
        return VerificationResult(
            factId = factId,
            verified = chainValid,
            contentHash = entry.contentHash,
            chainPosition = index,
            previousHash = entry.previousHash,
            ledgerTimestamp = entry.timestamp,
            verifiedAt = Instant.now(),
            message = if (chainValid) "Fact verified — chain integrity confirmed at position $index"
                      else "Chain integrity failure detected at or before position $index"
        )
    }

    override fun getHistory(factId: UUID): List<LedgerEntry> =
        entries.filter { it.factId == factId }

    override fun verifyChainIntegrity(from: Instant, to: Instant): ChainVerificationResult {
        val snapshot = entries.toList()
        val window = snapshot.filter { it.timestamp >= from && it.timestamp <= to }
        if (window.isEmpty()) {
            return ChainVerificationResult(
                valid = true,
                entriesChecked = 0,
                firstEntryTimestamp = null,
                lastEntryTimestamp = null,
                brokenAt = null,
                message = "No entries found in the specified time range"
            )
        }

        for (i in window.indices) {
            val entry = window[i]
            val globalIndex = snapshot.indexOf(entry)
            if (!isChainValidAt(snapshot, globalIndex)) {
                return ChainVerificationResult(
                    valid = false,
                    entriesChecked = i + 1,
                    firstEntryTimestamp = window.first().timestamp,
                    lastEntryTimestamp = window.last().timestamp,
                    brokenAt = entry.entryId,
                    message = "Chain integrity failure detected at entry ${entry.entryId}"
                )
            }
        }

        return ChainVerificationResult(
            valid = true,
            entriesChecked = window.size,
            firstEntryTimestamp = window.first().timestamp,
            lastEntryTimestamp = window.last().timestamp,
            brokenAt = null,
            message = "Chain integrity verified for ${window.size} entries"
        )
    }

    override fun listEntries(offset: Int, limit: Int): List<LedgerEntry> {
        val snapshot = entries.toList()
        val from = offset.coerceAtLeast(0)
        val to = (from + limit).coerceAtMost(snapshot.size)
        return if (from >= snapshot.size) emptyList() else snapshot.subList(from, to)
    }

    override fun countEntries(): Long = entries.size.toLong()

    private fun isChainValidAt(snapshot: List<LedgerEntry>, index: Int): Boolean {
        val entry = snapshot[index]
        val expectedPreviousHash = if (index == 0) GENESIS_HASH else computeEntryHash(snapshot[index - 1])
        return entry.previousHash == expectedPreviousHash
    }

    private fun computeEntryHash(entry: LedgerEntry): String =
        sha256("${entry.entryId}|${entry.factId}|${entry.eventType}|${entry.contentHash}|${entry.previousHash}|${entry.timestamp}")

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
