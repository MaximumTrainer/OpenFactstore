package com.factstore.adapter.outbound.signing

import com.factstore.core.port.outbound.EventSigningPublicKey
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.RecordSignature
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.util.Base64

/**
 * Development and test signer for stored events (#184): an Ed25519 key generated in memory at
 * startup and never written anywhere.
 *
 * Signatures are real, but the key dies with the process, so records signed before a restart can
 * no longer be verified. It is therefore refused under the `prod` profile: production uses
 * `factstore.event-signing.provider=vault-transit`, where the key lives in Vault.
 */
@Component
@ConditionalOnProperty(name = ["factstore.event-signing.provider"], havingValue = "local", matchIfMissing = true)
class LocalEventSigner(environment: Environment) : IEventRecordSigner {
    private val log = LoggerFactory.getLogger(LocalEventSigner::class.java)
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val created = Instant.now()
    private val keyId = "local:" + MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
        .take(8).joinToString("") { "%02x".format(it) }

    init {
        check(!environment.activeProfiles.contains("prod")) {
            "factstore.event-signing.provider=local is not allowed under the prod profile: its key is lost on restart. " +
                "Use factstore.event-signing.provider=vault-transit."
        }
        log.warn("Stored events are signed with an in-memory development key ({}); it is lost on restart", keyId)
    }

    override val algorithm: String = "Ed25519"

    override fun sign(canonicalBytes: ByteArray): RecordSignature = RecordSignature(
        keyId,
        Base64.getEncoder().encodeToString(
            Signature.getInstance(algorithm).run {
                initSign(keyPair.private)
                update(canonicalBytes)
                sign()
            }
        )
    )

    override fun publicKeys(): List<EventSigningPublicKey> =
        listOf(EventSigningPublicKey(keyId, algorithm, Base64.getEncoder().encodeToString(keyPair.public.encoded), created, null))
}
