package com.factstore.application.ledger

import com.factstore.application.signing.EventCanonicalForm
import com.factstore.application.signing.toCanonical
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.LedgerCheckpoint
import com.factstore.core.port.outbound.ICheckpointPublisher
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.IEventStore
import com.factstore.core.port.outbound.ILedgerCheckpointRepository
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

data class CheckpointView(
    @get:JsonProperty("checkpoint_index") val checkpointIndex: Long,
    @get:JsonProperty("first_sequence") val firstSequence: Long,
    @get:JsonProperty("last_sequence") val lastSequence: Long,
    @get:JsonProperty("leaf_count") val leafCount: Int,
    @get:JsonProperty("root_hash") val rootHash: String,
    val statement: String,
    val signature: String,
    @get:JsonProperty("signing_key_id") val signingKeyId: String,
    @get:JsonProperty("signature_algorithm") val signatureAlgorithm: String,
    @get:JsonProperty("created_at") val createdAt: Instant
)

data class ProofRecord(
    /** The record's canonical bytes (#184), base64: what its hash and signature cover. */
    val canonical: String,
    @get:JsonProperty("record_hash") val recordHash: String,
    val signature: String,
    @get:JsonProperty("signing_key_id") val signingKeyId: String,
    @get:JsonProperty("signature_algorithm") val signatureAlgorithm: String
)

data class MerkleProof(
    @get:JsonProperty("root_hash") val rootHash: String,
    @get:JsonProperty("leaf_index") val leafIndex: Int,
    @get:JsonProperty("tree_size") val treeSize: Int,
    val siblings: List<String>
)

data class LedgerProof(
    @get:JsonProperty("fact_id") val factId: UUID,
    @get:JsonProperty("ledger_index") val ledgerIndex: Long,
    val record: ProofRecord,
    @get:JsonProperty("merkle_proof") val merkleProof: MerkleProof,
    val checkpoint: CheckpointView,
    /** Whether the server could verify the record's hash and signature and the checkpoint's signature. */
    @get:JsonProperty("raw_signature_verified") val rawSignatureVerified: Boolean
)

/** A fact exists but no checkpoint covers it yet (#188). */
class ProofPendingException(message: String) : RuntimeException(message)

/**
 * Merkle checkpoints and inclusion proofs over the signed event log (#188).
 *
 * A checkpoint covers every signed record after the previous checkpoint, up to
 * `factstore.ledger.checkpoint-max-records`. Its leaves are the records' `record_hash` values in
 * sequence order, so the tree commits to the same bytes the record signatures (#184) do. The root
 * and range go into a canonical statement that the event-signing key signs. The checkpoint is
 * stored, and published once its transaction commits.
 */
@Service
class LedgerCheckpointService(
    private val eventStore: IEventStore,
    private val checkpoints: ILedgerCheckpointRepository,
    private val signer: IEventRecordSigner,
    private val publisher: ICheckpointPublisher,
    @Value("\${factstore.ledger.checkpoint-max-records:4096}") private val maxRecords: Int
) {
    private val log = LoggerFactory.getLogger(LedgerCheckpointService::class.java)

    /** Checkpoints the records not yet covered, or returns null when there are none. */
    @Transactional
    fun checkpoint(): CheckpointView? {
        val previous = checkpoints.findLatest()
        val from = (previous?.lastSequence ?: 0L) + 1
        val records = eventStore.findSignedInRange(from, Long.MAX_VALUE).take(maxRecords)
        if (records.isEmpty()) return null

        val root = HexFormat.of().formatHex(MerkleTree.root(records.map { HexFormat.of().parseHex(it.recordHash) }))
        val index = (previous?.checkpointIndex ?: 0L) + 1
        val statement = statement(index, records.first().sequenceNumber, records.last().sequenceNumber, records.size, root)
        val signature = signer.sign(statement.toByteArray(Charsets.UTF_8))
        val saved = checkpoints.save(
            LedgerCheckpoint(
                checkpointIndex = index,
                firstSequence = records.first().sequenceNumber,
                lastSequence = records.last().sequenceNumber,
                leafCount = records.size,
                rootHash = root,
                statement = statement,
                signature = signature.signature,
                signingKeyId = signature.keyId,
                signatureAlgorithm = signer.algorithm
            )
        )
        afterCommit { publisher.publish(saved) }
        log.info("Ledger checkpoint {} covers seq {}..{} ({} records), root {}", index, saved.firstSequence, saved.lastSequence, saved.leafCount, root)
        return saved.toView()
    }

    @Transactional(readOnly = true)
    fun list(): List<CheckpointView> = checkpoints.findAll().map { it.toView() }

    /** The inclusion proof for a fact's ingestion record. */
    @Transactional(readOnly = true)
    fun proof(factId: UUID): LedgerProof {
        val entry = eventStore.findByAggregateId(factId).firstOrNull { it.eventType == "FactIngested" }
            ?: throw NotFoundException("No fact $factId")
        if (entry.signatureStatus != EventLogEntry.SIGNED) throw NotFoundException("Fact $factId was stored before records were signed")
        val checkpoint = checkpoints.findCovering(entry.sequenceNumber)
            ?: throw ProofPendingException("Fact $factId is not in a checkpoint yet; retry after the next checkpoint")

        val leaves = eventStore.findSignedInRange(checkpoint.firstSequence, checkpoint.lastSequence)
        val leafIndex = leaves.indexOfFirst { it.sequenceNumber == entry.sequenceNumber }
        val leafBytes = leaves.map { HexFormat.of().parseHex(it.recordHash) }
        val path = MerkleTree.auditPath(leafBytes, leafIndex)
        val canonical = EventCanonicalForm.v1(entry.toCanonical(entry.prevHash!!))

        val keys = signer.publicKeys().associateBy { it.keyId }
        val verified = EventCanonicalForm.hash(canonical) == entry.recordHash &&
            leaves.size == checkpoint.leafCount &&
            HexFormat.of().formatHex(MerkleTree.root(leafBytes)) == checkpoint.rootHash &&
            verifies(keys[entry.signingKeyId], canonical, entry.signature) &&
            verifies(keys[checkpoint.signingKeyId], checkpoint.statement.toByteArray(Charsets.UTF_8), checkpoint.signature)

        return LedgerProof(
            factId = factId,
            ledgerIndex = entry.sequenceNumber,
            record = ProofRecord(
                canonical = Base64.getEncoder().encodeToString(canonical),
                recordHash = entry.recordHash!!,
                signature = entry.signature!!,
                signingKeyId = entry.signingKeyId!!,
                signatureAlgorithm = entry.signatureAlgorithm!!
            ),
            merkleProof = MerkleProof(checkpoint.rootHash, leafIndex, checkpoint.leafCount, path.map { HexFormat.of().formatHex(it) }),
            checkpoint = checkpoint.toView(),
            rawSignatureVerified = verified
        )
    }

    private fun verifies(key: com.factstore.core.port.outbound.EventSigningPublicKey?, data: ByteArray, signature: String?): Boolean =
        key != null && signature != null && runCatching {
            Signature.getInstance(key.algorithm).run {
                initVerify(KeyFactory.getInstance(key.algorithm).generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey))))
                update(data)
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)

    /** RFC 8785 JSON of the checkpoint's claims; every value is an integer or a hex string. */
    private fun statement(index: Long, first: Long, last: Long, leaves: Int, root: String): String =
        """{"checkpoint_index":$index,"first_sequence":$first,"last_sequence":$last,"leaf_count":$leaves,"root_hash":"$root","version":1}"""

    private fun afterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = action()
            })
        } else {
            action()
        }
    }

    private fun LedgerCheckpoint.toView() = CheckpointView(
        checkpointIndex, firstSequence, lastSequence, leafCount, rootHash, statement, signature, signingKeyId, signatureAlgorithm, createdAt
    )
}

/**
 * Checkpoints on a schedule (#188), every `factstore.ledger.checkpoint-interval-ms` (one minute by
 * default). Only where the event log lives: not on the CQRS query service.
 */
@Component
@ConditionalOnExpression("'\${factstore.cqrs.role:all}' != 'query'")
class LedgerCheckpointScheduler(private val service: LedgerCheckpointService) {
    @Scheduled(fixedDelayString = "\${factstore.ledger.checkpoint-interval-ms:60000}")
    fun checkpoint() {
        service.checkpoint()
    }
}
