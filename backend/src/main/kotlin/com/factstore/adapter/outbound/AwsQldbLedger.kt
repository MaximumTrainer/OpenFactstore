package com.factstore.adapter.outbound

import com.amazon.ion.IonInt
import com.amazon.ion.IonStruct
import com.amazon.ion.IonText
import com.amazon.ion.system.IonSystemBuilder
import com.factstore.config.LedgerProperties
import com.factstore.core.domain.ChainVerificationResult
import com.factstore.core.domain.LedgerEntry
import com.factstore.core.domain.LedgerRecord
import com.factstore.core.domain.LedgerReceipt
import com.factstore.core.domain.VerificationResult
import com.factstore.core.port.outbound.IImmutableLedger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.qldbsession.QldbSessionClient
import software.amazon.qldb.QldbDriver
import software.amazon.qldb.RetryPolicy
import software.amazon.qldb.TransactionExecutor
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * AWS QLDB adapter for the immutable ledger port.
 *
 * Activated when `ledger.enabled=true` and `ledger.type=qldb`.
 * Requires valid AWS credentials and a pre-created QLDB ledger with a "FactLedger" table.
 *
 * Setup QLDB table with:
 *   CREATE TABLE FactLedger
 *   CREATE INDEX ON FactLedger (factId)
 *   CREATE INDEX ON FactLedger (entryId)
 *
 * Each document stores `entryId` and `createdAt` as explicit fields so they survive
 * round-trips through SELECT * without relying on QLDB system metadata projection.
 *
 * Chain integrity verification is delegated to QLDB's native journal-digest mechanism
 * (GetDigest + GetRevision API) rather than being re-implemented client-side, since QLDB
 * provides cryptographic guarantees at the storage layer. The verifyChainIntegrity method
 * therefore returns verified=false with an explanatory message directing callers to use
 * the QLDB control-plane API for independent verification.
 */
@Component
@ConditionalOnExpression("\${ledger.enabled:false} and '\${ledger.type:local}' == 'qldb'")
class AwsQldbLedger(private val properties: LedgerProperties) : IImmutableLedger {

    private val log = LoggerFactory.getLogger(AwsQldbLedger::class.java)
    private val ionSystem = IonSystemBuilder.standard().build()

    private val driver: QldbDriver by lazy {
        QldbDriver.builder()
            .ledger(properties.qldb.ledgerName)
            .sessionClientBuilder(
                QldbSessionClient.builder().region(Region.of(properties.qldb.region))
            )
            .transactionRetryPolicy(RetryPolicy.maxRetries(3))
            .build()
            .also {
                log.info(
                    "AWS QLDB driver initialised (ledger={}, region={})",
                    properties.qldb.ledgerName,
                    properties.qldb.region
                )
            }
    }

    override fun recordFact(fact: LedgerRecord): LedgerReceipt {
        log.debug("Recording record {} to QLDB ledger {}", fact.recordId, properties.qldb.ledgerName)
        val contentHash = sha256(fact.content)
        val entryId = UUID.randomUUID().toString()
        val timestamp = Instant.now()

        driver.execute { txn: TransactionExecutor ->
            val document = ionSystem.newEmptyStruct().apply {
                // Store entryId and createdAt as explicit document fields so they can be read
                // back reliably via SELECT * without needing system-metadata projection.
                put("entryId", ionSystem.newString(entryId))
                put("factId", ionSystem.newString(fact.recordId.toString()))
                put("eventType", ionSystem.newString(fact.eventType))
                put("contentHash", ionSystem.newString(contentHash))
                put("createdAt", ionSystem.newString(timestamp.toString()))
                put("metadata", ionSystem.newString(
                    fact.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
                ))
            }
            txn.execute("INSERT INTO FactLedger ?", document)
        }

        return LedgerReceipt(
            entryId = entryId,
            recordId = fact.recordId,
            contentHash = contentHash,
            timestamp = timestamp
        )
    }

    override fun verifyFact(recordId: UUID): VerificationResult {
        log.debug("Verifying record {} in QLDB ledger {}", recordId, properties.qldb.ledgerName)
        var entry: LedgerEntry? = null

        driver.execute { txn: TransactionExecutor ->
            val result = txn.execute(
                "SELECT entryId, factId, eventType, contentHash, createdAt FROM FactLedger WHERE factId = ?",
                ionSystem.newString(recordId.toString())
            )
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                entry = struct.toLedgerEntry(recordId)
            }
        }

        val found = entry
        return if (found == null) {
            VerificationResult(
                recordId = recordId,
                verified = false,
                contentHash = null,
                chainPosition = null,
                previousHash = null,
                ledgerTimestamp = null,
                verifiedAt = Instant.now(),
                message = "No ledger entry found for recordId $recordId"
            )
        } else {
            // QLDB guarantees cryptographic integrity via its journal and digest mechanism.
            // Full cryptographic proof requires calling GetDigest + GetRevision via the QLDB control-plane client.
            VerificationResult(
                recordId = recordId,
                verified = true,
                contentHash = found.contentHash,
                chainPosition = null,
                previousHash = null,
                ledgerTimestamp = found.timestamp,
                verifiedAt = Instant.now(),
                message = "Record found in QLDB — use GetDigest+GetRevision for full cryptographic proof"
            )
        }
    }

    override fun getHistory(recordId: UUID): List<LedgerEntry> {
        val history = mutableListOf<LedgerEntry>()
        driver.execute { txn: TransactionExecutor ->
            // Project txTime (QLDB commit timestamp) alongside h.data.* for accurate timestamp resolution.
            // Documents store `entryId` and `createdAt` as explicit fields.
            val result = txn.execute(
                "SELECT h.metadata.txTime AS txTime, h.data.* " +
                    "FROM history(FactLedger) AS h WHERE h.data.factId = ?",
                ionSystem.newString(recordId.toString())
            )
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                val resolvedEntryId = resolveEntryId(struct)
                // Prefer QLDB txTime (accurate commit time) over createdAt (client time).
                val timestamp = resolveTimestamp(
                    txTimeStr = (struct.get("txTime") as? IonText)?.stringValue(),
                    createdAtStr = (struct.get("createdAt") as? IonText)?.stringValue()
                )
                history.add(
                    LedgerEntry(
                        entryId = resolvedEntryId,
                        recordId = recordId,
                        eventType = (struct.get("eventType") as? IonText)?.stringValue() ?: "",
                        contentHash = (struct.get("contentHash") as? IonText)?.stringValue() ?: "",
                        previousHash = "",
                        timestamp = timestamp,
                        metadata = emptyMap()
                    )
                )
            }
        }
        return history
    }

    override fun verifyChainIntegrity(from: Instant, to: Instant): ChainVerificationResult {
        // QLDB cryptographic integrity is provided natively by the ledger journal.
        // Client-side chain verification is not implemented for the QLDB adapter — integrity
        // must be confirmed via the QLDB control-plane GetDigest + GetRevision API, which
        // returns a cryptographic proof rooted in QLDB's immutable journal.
        //
        // Returning valid=false here is intentional: it signals to callers that no verification
        // was actually performed (rather than falsely asserting the chain is intact).
        log.info("QLDB chain integrity requested (from={}, to={}); delegating to QLDB native journal digest", from, to)
        return ChainVerificationResult(
            valid = false,
            entriesChecked = 0,
            firstEntryTimestamp = null,
            lastEntryTimestamp = null,
            brokenAt = null,
            message = "Client-side chain verification is not supported for the QLDB adapter. " +
                "Use the QLDB GetDigest + GetRevision API for cryptographic proof of ledger integrity."
        )
    }

    override fun listEntries(offset: Int, limit: Int): List<LedgerEntry> {
        val page = mutableListOf<LedgerEntry>()
        val safeOffset = offset.coerceAtLeast(0)
        driver.execute { txn: TransactionExecutor ->
            // Sort by createdAt for deterministic ordering before applying the offset window.
            val result = txn.execute(
                "SELECT entryId, factId, eventType, contentHash, createdAt " +
                    "FROM FactLedger ORDER BY createdAt"
            )
            var index = 0
            result.forEach { doc ->
                if (index >= safeOffset && page.size < limit) {
                    val struct = doc as? IonStruct
                    if (struct != null) {
                        val recordIdStr = (struct.get("factId") as? IonText)?.stringValue()
                        val parsedRecordId = recordIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: UUID.randomUUID()
                        page.add(struct.toLedgerEntry(parsedRecordId))
                    }
                }
                index++
            }
        }
        return page
    }

    override fun countEntries(): Long {
        var count = 0L
        driver.execute { txn: TransactionExecutor ->
            val result = txn.execute("SELECT COUNT(*) AS cnt FROM FactLedger")
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                count = (struct.get("cnt") as? IonInt)?.longValue() ?: 0L
            }
        }
        return count
    }

    /**
     * Maps an Ion struct (from a SELECT query row) to a [LedgerEntry].
     * Reads `entryId` and `createdAt` from the document fields stored at insert time.
     */
    private fun IonStruct.toLedgerEntry(recordId: UUID): LedgerEntry = LedgerEntry(
        entryId = (this.get("entryId") as? IonText)?.stringValue() ?: "",
        recordId = recordId,
        eventType = (this.get("eventType") as? IonText)?.stringValue() ?: "",
        contentHash = (this.get("contentHash") as? IonText)?.stringValue() ?: "",
        previousHash = "",
        timestamp = resolveTimestamp(createdAtStr = (this.get("createdAt") as? IonText)?.stringValue()),
        metadata = emptyMap()
    )

    private fun resolveEntryId(struct: IonStruct): String = Companion.resolveEntryId(struct)

    private fun resolveTimestamp(txTimeStr: String? = null, createdAtStr: String? = null): Instant =
        Companion.resolveTimestamp(txTimeStr, createdAtStr)

    companion object {
        /**
         * Resolves the entryId from a QLDB history IonStruct.
         *
         * Reads the explicit `entryId` field stored in every document since the schema migration.
         * Returns an empty string when the field is absent or is not a string value.
         *
         * Covered by [AwsQldbLedgerEntryResolutionTest].
         */
        internal fun resolveEntryId(struct: IonStruct): String =
            (struct.get("entryId") as? IonText)?.stringValue() ?: ""

        /**
         * Resolves an [Instant] from QLDB history metadata timestamps or a stored `createdAt` field.
         * Priority: txTime (QLDB commit time) → createdAt (client-recorded ISO string) → now().
         *
         * Covered by [AwsQldbLedgerEntryResolutionTest].
         */
        internal fun resolveTimestamp(txTimeStr: String? = null, createdAtStr: String? = null): Instant =
            txTimeStr?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: createdAtStr?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: Instant.now()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
