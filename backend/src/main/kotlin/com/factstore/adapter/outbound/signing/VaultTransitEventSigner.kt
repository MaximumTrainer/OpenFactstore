package com.factstore.adapter.outbound.signing

import com.factstore.core.port.outbound.EventSigningPublicKey
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.RecordSignature
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.Plaintext
import java.time.Instant
import java.util.Base64

/** One version of a Vault Transit key: its raw Ed25519 public key (base64) and when it was created. */
data class TransitKeyVersion(val version: Int, val publicKey: String, val createdAt: Instant)

/** The two Vault Transit operations event signing needs: sign, and read the public keys. */
interface TransitSigningClient {
    /** Vault's signature string, `vault:v<version>:<base64>`. */
    fun sign(keyName: String, bytes: ByteArray): String
    fun keyVersions(keyName: String): List<TransitKeyVersion>
}

@Component
@ConditionalOnProperty(name = ["factstore.event-signing.provider"], havingValue = "vault-transit")
class VaultTemplateTransitClient(private val vault: VaultTemplate) : TransitSigningClient {
    override fun sign(keyName: String, bytes: ByteArray): String =
        vault.opsForTransit().sign(keyName, Plaintext.of(bytes)).signature

    override fun keyVersions(keyName: String): List<TransitKeyVersion> {
        val key = vault.opsForTransit().getKey(keyName) ?: error("Vault Transit key '$keyName' does not exist")
        return key.keys.map { (version, details) ->
            val fields = details as Map<*, *>
            TransitKeyVersion(
                version = version.toInt(),
                publicKey = fields["public_key"] as String,
                createdAt = Instant.parse(fields["creation_time"] as String)
            )
        }
    }
}

/**
 * Signs stored events with an Ed25519 key in Vault Transit (#184). The private key never leaves
 * Vault: Factstore calls `transit/sign/<key>` and reads public keys from `transit/keys/<key>`.
 *
 * Key ids are `<key name>:v<version>`. Vault always signs with the latest version, so rotating
 * the key in Vault (`transit/keys/<key>/rotate`) moves new records onto the new version. Old
 * versions stay published, with a validity window that ends when the next version was created,
 * so earlier records keep verifying and nothing is re-signed. Create the key with
 * `type=ed25519`; see `docs/runbooks/event-signing-keys.md`.
 */
@Component
@ConditionalOnProperty(name = ["factstore.event-signing.provider"], havingValue = "vault-transit")
class VaultTransitEventSigner(
    private val client: TransitSigningClient,
    @Value("\${factstore.event-signing.vault.key-name:factstore-events}") private val keyName: String
) : IEventRecordSigner {

    override val algorithm: String = "Ed25519"

    override fun sign(canonicalBytes: ByteArray): RecordSignature {
        val parts = client.sign(keyName, canonicalBytes).split(":")
        require(parts.size == 3 && parts[0] == "vault" && parts[1].startsWith("v")) { "Unexpected Vault signature format" }
        return RecordSignature("$keyName:${parts[1]}", parts[2])
    }

    override fun publicKeys(): List<EventSigningPublicKey> {
        val versions = client.keyVersions(keyName).sortedBy { it.version }
        return versions.mapIndexed { i, v ->
            EventSigningPublicKey(
                keyId = "$keyName:v${v.version}",
                algorithm = algorithm,
                publicKey = Base64.getEncoder().encodeToString(ED25519_SPKI_PREFIX + Base64.getDecoder().decode(v.publicKey)),
                validFrom = v.createdAt,
                validTo = versions.getOrNull(i + 1)?.createdAt
            )
        }
    }

    companion object {
        /** DER prefix turning a raw 32-byte Ed25519 key into X.509 SubjectPublicKeyInfo (RFC 8410). */
        private val ED25519_SPKI_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )
    }
}
