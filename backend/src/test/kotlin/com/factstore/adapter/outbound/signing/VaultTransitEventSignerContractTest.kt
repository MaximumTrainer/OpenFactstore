package com.factstore.adapter.outbound.signing

import com.factstore.application.signing.CanonicalEventRecord
import com.factstore.application.signing.EventCanonicalForm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * #184 — the Vault Transit signer against Vault's wire shapes: `vault:v<n>:<base64>`
 * signatures and raw base64 Ed25519 public keys per key version. Vault itself is replaced by an
 * in-memory stand-in that holds real Ed25519 keys and signs as Transit does.
 */
class VaultTransitEventSignerContractTest {

    private class FakeTransit : TransitSigningClient {
        val versions = mutableListOf<Pair<KeyPair, Instant>>()
        fun rotate(at: Instant) { versions += KeyPairGenerator.getInstance("Ed25519").generateKeyPair() to at }

        override fun sign(keyName: String, bytes: ByteArray): String {
            val (pair, _) = versions.last()
            val sig = Signature.getInstance("Ed25519").run { initSign(pair.private); update(bytes); sign() }
            return "vault:v${versions.size}:${Base64.getEncoder().encodeToString(sig)}"
        }

        override fun keyVersions(keyName: String) = versions.mapIndexed { i, (pair, at) ->
            // Vault returns the raw 32-byte key: the last 32 bytes of the X.509 encoding.
            TransitKeyVersion(i + 1, Base64.getEncoder().encodeToString(pair.public.encoded.takeLast(32).toByteArray()), at)
        }.reversed()
    }

    private val transit = FakeTransit().apply { rotate(Instant.parse("2026-01-01T00:00:00Z")) }
    private val signer = VaultTransitEventSigner(transit, "factstore-events")
    private val canonical = EventCanonicalForm.v1(
        CanonicalEventRecord(1, UUID.randomUUID(), UUID.randomUUID(), "Fact", "FactIngested", Instant.now(), "{}", EventCanonicalForm.GENESIS_HASH)
    )

    private fun verifies(keyId: String, signature: String): Boolean {
        val key = signer.publicKeys().single { it.keyId == keyId }
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey)))
        return Signature.getInstance("Ed25519").run { initVerify(publicKey); update(canonical); verify(Base64.getDecoder().decode(signature)) }
    }

    @Test
    fun `#184 a Transit signature names the key version and verifies under the published key`() {
        val signature = signer.sign(canonical)
        assertThat(signature.keyId).isEqualTo("factstore-events:v1")
        assertThat(verifies(signature.keyId, signature.signature)).isTrue()
    }

    @Test
    fun `#184 after rotation in Vault, old and new versions are published with validity windows`() {
        val old = signer.sign(canonical)
        transit.rotate(Instant.parse("2026-06-01T00:00:00Z"))
        val new = signer.sign(canonical)

        assertThat(new.keyId).isEqualTo("factstore-events:v2")
        val keys = signer.publicKeys()
        assertThat(keys.map { it.keyId }).containsExactly("factstore-events:v1", "factstore-events:v2")
        assertThat(keys[0].validTo).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"))
        assertThat(keys[1].validTo).isNull()
        assertThat(verifies(old.keyId, old.signature)).isTrue()
        assertThat(verifies(new.keyId, new.signature)).isTrue()
    }
}
