package com.factstore.application.shredding

import com.factstore.core.domain.DataKey
import com.factstore.core.domain.DataKeyStatus
import com.factstore.core.port.outbound.IDataKeyRepository
import com.factstore.core.port.outbound.IKeyEncryptionKey
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A fact's data key has been shredded: its encrypted content is gone for good (#200). */
class FactShreddedException(val tenant: String, val period: String) :
    RuntimeException("The data key for $tenant/$period has been shredded; this content no longer exists")

/**
 * Encrypts fact content under the data key for its tenant and month (#200).
 *
 * The format is `enc:v1:<tenant>:<period>:<base64(nonce || AES-256-GCM ciphertext)>`, with the
 * fact id as additional authenticated data, so ciphertext cannot be moved to another fact. Data
 * keys are created on first use, stored only wrapped by the [IKeyEncryptionKey], and cached
 * unwrapped in memory. The key's status is checked in the database on every decryption, so a key
 * shredded by any instance stops decryption on every instance at once.
 */
@Component
class PayloadEncryption(
    private val keys: IDataKeyRepository,
    private val kek: IKeyEncryptionKey
) {
    private val random = SecureRandom()
    private val unwrapped = ConcurrentHashMap<String, ByteArray>()

    fun periodOf(instant: Instant): String = PERIOD.format(instant)

    fun encrypt(tenant: String, period: String, factId: UUID, plaintext: String): String {
        val key = activeKey(tenant, period)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(factId.toString().toByteArray())
        }
        val sealed = nonce + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$PREFIX$tenant:$period:${Base64.getEncoder().encodeToString(sealed)}"
    }

    /** The plaintext, or [FactShreddedException] once the key is gone. Plaintext from before #200 passes through. */
    fun decrypt(factId: UUID, stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        val (tenant, period, body) = stored.removePrefix(PREFIX).split(":", limit = 3)
        val record = keys.find(tenant, period)
        if (record == null || record.status == DataKeyStatus.SHREDDED || record.wrappedKey == null) {
            unwrapped.remove(cacheKey(tenant, period))
            throw FactShreddedException(tenant, period)
        }
        val key = unwrapped.getOrPut(cacheKey(tenant, period)) { kek.unwrap(record.wrappedKey!!) }
        val bytes = Base64.getDecoder().decode(body)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, NONCE_BYTES)))
            updateAAD(factId.toString().toByteArray())
        }
        return String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
    }

    /** Forget a shredded key on this instance at once. */
    fun forget(tenant: String, period: String) {
        unwrapped.remove(cacheKey(tenant, period))?.fill(0)
    }

    private fun activeKey(tenant: String, period: String): ByteArray {
        unwrapped[cacheKey(tenant, period)]?.let { cached ->
            if (keys.find(tenant, period)?.status == DataKeyStatus.ACTIVE) return cached
        }
        val existing = keys.find(tenant, period)
        if (existing != null) {
            if (existing.status == DataKeyStatus.SHREDDED || existing.wrappedKey == null) {
                // A shredded period is closed: nothing new may be written under it.
                throw FactShreddedException(tenant, period)
            }
            return unwrapped.getOrPut(cacheKey(tenant, period)) { kek.unwrap(existing.wrappedKey!!) }
        }
        val fresh = ByteArray(KEY_BYTES).also(random::nextBytes)
        keys.save(DataKey(tenant = tenant, period = period, wrappedKey = kek.wrap(fresh), kekId = kek.keyId))
        unwrapped[cacheKey(tenant, period)] = fresh
        return fresh
    }

    private fun cacheKey(tenant: String, period: String) = "$tenant/$period"

    companion object {
        const val PREFIX = "enc:v1:"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BYTES = 32
        private val PERIOD = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)
    }
}
