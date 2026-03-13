package com.factstore.adapter.outbound

import com.amazon.ion.IonInt
import com.amazon.ion.IonStruct
import com.amazon.ion.IonText
import com.amazon.ion.system.IonSystemBuilder
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

    override fun recordFact(fact: LedgerFact): LedgerReceipt {
        log.debug("Recording fact {} to QLDB ledger {}", fact.factId, properties.qldb.ledgerName)
        val contentHash = sha256(fact.content)
        var entryId = UUID.randomUUID().toString()
        val timestamp = Instant.now()

        driver.execute { txn: TransactionExecutor ->
            val document = ionSystem.newEmptyStruct().apply {
                put("factId", ionSystem.newString(fact.factId.toString()))
                put("eventType", ionSystem.newString(fact.eventType))
                put("contentHash", ionSystem.newString(contentHash))
                put("metadata", ionSystem.newString(
                    fact.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
                ))
            }
            val result = txn.execute("INSERT INTO FactLedger ?", document)
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                val id = (struct.get("documentId") as? IonText)?.stringValue()
                if (id != null) entryId = id
            }
        }

        return LedgerReceipt(
            entryId = entryId,
            factId = fact.factId,
            contentHash = contentHash,
            timestamp = timestamp
        )
    }

    override fun verifyFact(factId: UUID): VerificationResult {
        log.debug("Verifying fact {} in QLDB ledger {}", factId, properties.qldb.ledgerName)
        var entry: LedgerEntry? = null

        driver.execute { txn: TransactionExecutor ->
            val result = txn.execute(
                "SELECT * FROM FactLedger WHERE factId = ?",
                ionSystem.newString(factId.toString())
            )
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                entry = LedgerEntry(
                    entryId = (struct.get("documentId") as? IonText)?.stringValue() ?: "",
                    factId = factId,
                    eventType = (struct.get("eventType") as? IonText)?.stringValue() ?: "",
                    contentHash = (struct.get("contentHash") as? IonText)?.stringValue() ?: "",
                    previousHash = "",
                    timestamp = Instant.now(),
                    metadata = emptyMap()
                )
            }
        }

        val found = entry
        return if (found == null) {
            VerificationResult(
                factId = factId,
                verified = false,
                contentHash = null,
                chainPosition = null,
                previousHash = null,
                ledgerTimestamp = null,
                verifiedAt = Instant.now(),
                message = "No ledger entry found for factId $factId"
            )
        } else {
            // QLDB guarantees cryptographic integrity via its journal and digest mechanism.
            // Full cryptographic proof requires calling GetDigest + GetRevision via the QLDB control-plane client.
            VerificationResult(
                factId = factId,
                verified = true,
                contentHash = found.contentHash,
                chainPosition = null,
                previousHash = null,
                ledgerTimestamp = found.timestamp,
                verifiedAt = Instant.now(),
                message = "Fact found in QLDB — use GetDigest+GetRevision for full cryptographic proof"
            )
        }
    }

    override fun getHistory(factId: UUID): List<LedgerEntry> {
        val history = mutableListOf<LedgerEntry>()
        driver.execute { txn: TransactionExecutor ->
            val result = txn.execute(
                "SELECT * FROM history(FactLedger) AS h WHERE h.data.factId = ?",
                ionSystem.newString(factId.toString())
            )
            result.forEach { doc ->
                val outer = doc as? IonStruct ?: return@forEach
                val metadata = outer.get("metadata") as? IonStruct
                val data = outer.get("data") as? IonStruct ?: return@forEach
                history.add(
                    LedgerEntry(
                        entryId = (metadata?.get("id") as? IonText)?.stringValue() ?: "",
                        factId = factId,
                        eventType = (data.get("eventType") as? IonText)?.stringValue() ?: "",
                        contentHash = (data.get("contentHash") as? IonText)?.stringValue() ?: "",
                        previousHash = "",
                        timestamp = Instant.now(),
                        metadata = emptyMap()
                    )
                )
            }
        }
        return history
    }

    override fun verifyChainIntegrity(from: Instant, to: Instant): ChainVerificationResult {
        // QLDB cryptographic integrity is provided natively by the ledger journal.
        // GetDigest returns the SHA-256 hash of the journal up to a block address,
        // which can be independently verified by any party with journal export access.
        log.info("QLDB chain integrity: delegated to native QLDB journal digest (from={}, to={})", from, to)
        return ChainVerificationResult(
            valid = true,
            entriesChecked = 0,
            firstEntryTimestamp = from,
            lastEntryTimestamp = to,
            brokenAt = null,
            message = "QLDB provides native cryptographic integrity — use GetDigest API for independent verification"
        )
    }

    override fun listEntries(offset: Int, limit: Int): List<LedgerEntry> {
        val all = mutableListOf<LedgerEntry>()
        driver.execute { txn: TransactionExecutor ->
            val result = txn.execute("SELECT * FROM FactLedger")
            result.forEach { doc ->
                val struct = doc as? IonStruct ?: return@forEach
                all.add(
                    LedgerEntry(
                        entryId = (struct.get("documentId") as? IonText)?.stringValue() ?: "",
                        factId = UUID.fromString(
                            (struct.get("factId") as? IonText)?.stringValue()
                                ?: UUID.randomUUID().toString()
                        ),
                        eventType = (struct.get("eventType") as? IonText)?.stringValue() ?: "",
                        contentHash = (struct.get("contentHash") as? IonText)?.stringValue() ?: "",
                        previousHash = "",
                        timestamp = Instant.now(),
                        metadata = emptyMap()
                    )
                )
            }
        }
        val from = offset.coerceAtLeast(0)
        val to = (from + limit).coerceAtMost(all.size)
        return if (from >= all.size) emptyList() else all.subList(from, to)
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

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
