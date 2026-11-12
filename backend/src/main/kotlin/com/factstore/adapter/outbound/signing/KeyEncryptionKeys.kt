package com.factstore.adapter.outbound.signing

import com.factstore.core.port.outbound.IKeyEncryptionKey
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.Ciphertext
import org.springframework.vault.support.Plaintext
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Development and test key-encryption key (#200): an AES-256 key generated in memory and never
 * written anywhere. Data keys it wrapped cannot be unwrapped after a restart, so it is refused
 * under the `prod` profile; production uses `factstore.data-keys.provider=vault-transit`.
 */
@Component
@ConditionalOnProperty(name = ["factstore.data-keys.provider"], havingValue = "local", matchIfMissing = true)
class LocalKeyEncryptionKey(environment: Environment) : IKeyEncryptionKey {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    init {
        check(!environment.activeProfiles.contains("prod")) {
            "factstore.data-keys.provider=local is not allowed under the prod profile: its key is lost on restart. " +
                "Use factstore.data-keys.provider=vault-transit."
        }
        LoggerFactory.getLogger(LocalKeyEncryptionKey::class.java)
            .warn("Data keys are wrapped with an in-memory development key; encrypted facts are unreadable after a restart")
    }

    override val keyId: String = "local"

    override fun wrap(dataKey: ByteArray): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(dataKey))
    }

    override fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.getDecoder().decode(wrapped)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
}

/**
 * Wraps data keys with a Vault Transit key (#200), `transit/encrypt` and `transit/decrypt` on
 * `factstore.data-keys.vault.key-name` (default `factstore-data-keys`). The key-encryption key
 * never leaves Vault, and rotating it in Vault does not affect data keys already wrapped.
 */
@Component
@ConditionalOnProperty(name = ["factstore.data-keys.provider"], havingValue = "vault-transit")
class VaultTransitKeyEncryptionKey(
    private val vault: VaultTemplate,
    @Value("\${factstore.data-keys.vault.key-name:factstore-data-keys}") private val keyName: String
) : IKeyEncryptionKey {
    override val keyId: String = "vault-transit:$keyName"

    override fun wrap(dataKey: ByteArray): String =
        vault.opsForTransit().encrypt(keyName, Plaintext.of(dataKey)).ciphertext

    override fun unwrap(wrapped: String): ByteArray =
        vault.opsForTransit().decrypt(keyName, Ciphertext.of(wrapped)).plaintext
}
