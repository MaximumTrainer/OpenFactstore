package com.factstore.application.signing

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IEventChainHead
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.IEventStore
import com.factstore.exception.EventSigningException
import org.springframework.stereotype.Component

/**
 * Appends an event record and signs it in the same transaction (#184).
 *
 * 1. Lock the chain head, so concurrent appends are chained in sequence order.
 * 2. Insert the record, which assigns its sequence number.
 * 3. Build its canonical bytes ([EventCanonicalForm.v1]), linking it to the head's hash.
 * 4. Have the key service sign them. The private key never reaches this process.
 * 5. Store the signature, key id, algorithm, canonical version and hashes, and advance the head.
 *
 * A signing failure is an [EventSigningException]. The surrounding transaction rolls back, so no
 * unsigned record and no partial write is left behind.
 */
@Component
class EventRecordSealer(
    private val eventStore: IEventStore,
    private val chainHead: IEventChainHead,
    private val signer: IEventRecordSigner
) {

    fun appendSealed(entry: EventLogEntry): EventLogEntry {
        val head = chainHead.lockForAppend()
        val saved = eventStore.append(entry)
        val canonical = EventCanonicalForm.v1(saved.toCanonical(head.lastHash))
        val signature = try {
            signer.sign(canonical)
        } catch (e: Exception) {
            throw EventSigningException("Could not sign event ${saved.eventId} (seq ${saved.sequenceNumber})", e)
        }
        val hash = EventCanonicalForm.hash(canonical)
        saved.signature = signature.signature
        saved.signingKeyId = signature.keyId
        saved.signatureAlgorithm = signer.algorithm
        saved.canonicalVersion = EventCanonicalForm.V1
        saved.prevHash = head.lastHash
        saved.recordHash = hash
        saved.signatureStatus = EventLogEntry.SIGNED
        val sealed = eventStore.recordSignature(saved)
        chainHead.advance(sealed.sequenceNumber, hash)
        return sealed
    }
}

/** The canonical record for a stored entry, linked to [prevHash]. */
fun EventLogEntry.toCanonical(prevHash: String) = CanonicalEventRecord(
    sequenceNumber = sequenceNumber,
    eventId = eventId,
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    eventType = eventType,
    occurredAt = occurredAt,
    payload = payload,
    prevHash = prevHash
)
