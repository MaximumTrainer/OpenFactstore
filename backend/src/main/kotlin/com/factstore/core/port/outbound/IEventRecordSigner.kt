package com.factstore.core.port.outbound

import java.time.Instant

/** A signature over a stored event record's canonical bytes, and the key version that made it (#184). */
data class RecordSignature(val keyId: String, val signature: String)

/**
 * A published verification key (#184): [publicKey] is the X.509 SubjectPublicKeyInfo, base64.
 * [validTo] is null for the key currently signing.
 */
data class EventSigningPublicKey(
    val keyId: String,
    val algorithm: String,
    val publicKey: String,
    val validFrom: java.time.Instant,
    val validTo: Instant?
)

/**
 * Signs stored event records with a private key the application never holds (#184): the key
 * lives in a key service (Vault Transit, a cloud KMS) and only the sign operation is called.
 */
interface IEventRecordSigner {
    val algorithm: String
    fun sign(canonicalBytes: ByteArray): RecordSignature
    /** Every key version that has signed records, oldest first, so old records keep verifying. */
    fun publicKeys(): List<EventSigningPublicKey>
}
