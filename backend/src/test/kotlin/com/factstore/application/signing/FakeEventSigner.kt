package com.factstore.application.signing

import com.factstore.core.port.outbound.EventSigningPublicKey
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.RecordSignature
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64

/**
 * A stand-in for the key service that signs stored events (#184), for tests. Real Ed25519
 * signatures, keys held in memory, and two levers: [rotate] to a new key version, and
 * [failNextSign] to make the next signing call fail as an unreachable key service would.
 */
class FakeEventSigner : IEventRecordSigner {
    private data class Version(val keyId: String, val keyPair: KeyPair, val validFrom: Instant, var validTo: Instant? = null)

    private val versions = mutableListOf<Version>()
    @Volatile var failNextSign = false

    init {
        rotate()
    }

    fun rotate(): String {
        val now = Instant.now()
        versions.lastOrNull()?.validTo = now
        val version = Version("fake-events:v${versions.size + 1}", KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), now)
        versions += version
        return version.keyId
    }

    override val algorithm: String = "Ed25519"

    override fun sign(canonicalBytes: ByteArray): RecordSignature {
        if (failNextSign) {
            failNextSign = false
            throw IllegalStateException("key service unavailable")
        }
        val current = versions.last()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(current.keyPair.private)
            update(canonicalBytes)
            sign()
        }
        return RecordSignature(current.keyId, Base64.getEncoder().encodeToString(signature))
    }

    override fun publicKeys(): List<EventSigningPublicKey> = versions.map {
        EventSigningPublicKey(it.keyId, algorithm, Base64.getEncoder().encodeToString(it.keyPair.public.encoded), it.validFrom, it.validTo)
    }
}
