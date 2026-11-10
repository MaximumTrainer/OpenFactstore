package com.factstore.application.signing

import com.factstore.application.observability.FailureMetrics
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.EventSigningPublicKey
import com.factstore.core.port.outbound.IEventChainHead
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.IEventStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class VerificationFailure(val sequenceNumber: Long, val reason: String)

data class VerificationReport(
    val from: Long,
    val to: Long,
    /** Signed records checked before the first failure, or all of them. */
    val checked: Int,
    /** Records in the range stored before signing (#184): flagged, not failed. */
    val unsigned: Long,
    val valid: Boolean,
    val firstFailure: VerificationFailure?
)

/**
 * Verifies stored event records over a sequence range (#184). For each signed record, in order:
 *
 * - its `prev_hash` must be the hash of the signed record before it, so a deleted or reordered
 *   record breaks the chain at the record after it;
 * - its canonical bytes are rebuilt from the stored columns, and their hash must equal
 *   `record_hash`, so a changed column is caught;
 * - its signature must verify under the published key named by `signing_key_id`.
 *
 * When the range reaches the newest record, the last hash must also equal the chain head, so
 * deleting the newest record is caught too. The report names the first failing sequence number.
 */
@Service
class EventChainVerifier(
    private val eventStore: IEventStore,
    private val chainHead: IEventChainHead,
    private val signer: IEventRecordSigner,
    private val failureMetrics: FailureMetrics
) {
    private val log = LoggerFactory.getLogger(EventChainVerifier::class.java)

    @Transactional(readOnly = true)
    fun verify(from: Long, to: Long): VerificationReport {
        require(from in 1..to) { "from must be at least 1 and no greater than to" }
        val keys = signer.publicKeys().associateBy { it.keyId }
        var expectedPrev = eventStore.findLastSignedBefore(from)?.recordHash ?: EventCanonicalForm.GENESIS_HASH
        var lastSequence: Long? = null
        var checked = 0

        for (entry in eventStore.findSignedInRange(from, to)) {
            val reason = check(entry, expectedPrev, keys)
            if (reason != null) return failed(from, to, checked, VerificationFailure(entry.sequenceNumber, reason))
            expectedPrev = entry.recordHash!!
            lastSequence = entry.sequenceNumber
            checked++
        }

        val head = chainHead.read()
        if (head.lastSequence != null && to >= head.lastSequence && expectedPrev != head.lastHash) {
            val reason = if (lastSequence == head.lastSequence) "record does not match the chain head"
            else "the newest record (seq ${head.lastSequence}) is missing or altered"
            return failed(from, to, checked, VerificationFailure(head.lastSequence, reason))
        }
        return VerificationReport(from, to, checked, eventStore.countUnsignedInRange(from, to), valid = true, firstFailure = null)
    }

    private fun check(entry: EventLogEntry, expectedPrev: String, keys: Map<String, EventSigningPublicKey>): String? {
        if (entry.canonicalVersion != EventCanonicalForm.V1) return "unknown canonical version ${entry.canonicalVersion}"
        if (entry.prevHash != expectedPrev) return "chain broken: the previous record is missing, reordered or altered"
        val canonical = EventCanonicalForm.v1(entry.toCanonical(entry.prevHash!!))
        if (EventCanonicalForm.hash(canonical) != entry.recordHash) return "record altered: its content does not match its hash"
        val key = keys[entry.signingKeyId] ?: return "signed by unknown key ${entry.signingKeyId}"
        if (!signatureVerifies(key, canonical, entry.signature)) return "signature does not verify under ${entry.signingKeyId}"
        return null
    }

    private fun signatureVerifies(key: EventSigningPublicKey, canonical: ByteArray, signature: String?): Boolean =
        runCatching {
            Signature.getInstance(key.algorithm).run {
                initVerify(publicKey(key))
                update(canonical)
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)

    private fun publicKey(key: EventSigningPublicKey): PublicKey =
        KeyFactory.getInstance(key.algorithm).generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey)))

    private fun failed(from: Long, to: Long, checked: Int, failure: VerificationFailure): VerificationReport {
        failureMetrics.integrityFailure(FailureMetrics.IntegritySource.SIGNING)
        log.error("Event record verification failed at seq {}: {}", failure.sequenceNumber, failure.reason)
        return VerificationReport(from, to, checked, eventStore.countUnsignedInRange(from, to), valid = false, firstFailure = failure)
    }
}
