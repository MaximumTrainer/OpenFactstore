package com.factstore.application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The AES-256 key SCM tokens are encrypted with (#242).
 *
 * `SCM_ENCRYPTION_KEY` must be the base64 encoding of exactly 32 random bytes
 * (`openssl rand -base64 32`). Nothing is padded, truncated or derived: a short passphrase is
 * refused rather than quietly turned into a weak key. Outside the `dev` and `local` profiles
 * there is no default, and the development key is refused.
 */
object ScmEncryptionKeys {
    /** What every deployment used before #242; refused everywhere. */
    const val LEGACY_DEV_LITERAL = "default-dev-key-32chars!!!!!!"

    /** Used, and only accepted, in the `dev` and `local` profiles. Public by design: never a secret. */
    const val DEV_KEY = "ZmFjdHN0b3JlLWRldi1vbmx5LXNjbS1rZXktMzJieXQ="

    val DEV_PROFILES = arrayOf("dev", "local")

    private const val HOW = "Generate one with `openssl rand -base64 32` and set SCM_ENCRYPTION_KEY."

    fun parse(raw: String?, devProfile: Boolean): SecretKey {
        val value = raw?.trim().orEmpty()
        if (value == LEGACY_DEV_LITERAL) {
            throw IllegalStateException(
                "SCM_ENCRYPTION_KEY is the legacy development key '$LEGACY_DEV_LITERAL', which is public. $HOW " +
                    "Tokens encrypted with it can be moved to the new key; see docs/security-hardening.md."
            )
        }
        if (value.isEmpty()) {
            if (devProfile) return parse(DEV_KEY, devProfile = true)
            throw IllegalStateException("SCM_ENCRYPTION_KEY is not set, and there is no default outside the dev profile. $HOW")
        }
        if (value == DEV_KEY && !devProfile) {
            throw IllegalStateException(
                "SCM_ENCRYPTION_KEY is the public development key, which is accepted only in the " +
                    "${DEV_PROFILES.joinToString("/")} profiles. $HOW"
            )
        }
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("SCM_ENCRYPTION_KEY must be base64-encoded. $HOW")
        }
        if (bytes.size != 32) {
            throw IllegalStateException(
                "SCM_ENCRYPTION_KEY must decode to exactly 32 bytes, but decodes to ${bytes.size}. $HOW"
            )
        }
        return SecretKeySpec(bytes, "AES")
    }

    /** How keys were made before #242: the raw string's bytes, zero-padded or truncated to 32. */
    fun legacy(raw: String): SecretKey = SecretKeySpec(raw.toByteArray(Charsets.UTF_8).copyOf(32), "AES")
}

/**
 * Encrypts SCM tokens at rest with AES-256-GCM. With `SCM_LEGACY_ENCRYPTION_KEY` set to the old
 * passphrase, tokens encrypted before #242 still decrypt, and [ScmTokenReEncryption] moves them
 * to the new key.
 */
@Service
class ScmTokenEncryptionService(
    private val key: SecretKey,
    private val legacyKey: SecretKey?
) {
    /** For tests and tools: a base64 key and optionally the old passphrase. */
    constructor(rawKey: String, legacyRawKey: String = "", devProfile: Boolean = false) : this(
        ScmEncryptionKeys.parse(rawKey, devProfile),
        legacyRawKey.takeIf { it.isNotBlank() }?.let { ScmEncryptionKeys.legacy(it) }
    )

    @Autowired
    constructor(
        @Value("\${factstore.scm.encryption-key:}") rawKey: String,
        @Value("\${factstore.scm.legacy-encryption-key:}") legacyRawKey: String,
        environment: Environment
    ) : this(rawKey, legacyRawKey, environment.acceptsProfiles(Profiles.of(*ScmEncryptionKeys.DEV_PROFILES)))

    private val gcmIvLength = 12
    private val gcmTagLength = 128

    val hasLegacyKey: Boolean get() = legacyKey != null

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(gcmIvLength).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(ciphertext: String): String =
        try {
            decryptWith(key, ciphertext)
        } catch (e: AEADBadTagException) {
            decryptWith(legacyKey ?: throw e, ciphertext)
        }

    /** True when [ciphertext] only decrypts with the legacy key. */
    fun isLegacy(ciphertext: String): Boolean =
        try {
            decryptWith(key, ciphertext)
            false
        } catch (e: AEADBadTagException) {
            legacyKey != null
        }

    private fun decryptWith(k: SecretKey, ciphertext: String): String {
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, gcmIvLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(gcmTagLength, iv))
        return String(cipher.doFinal(combined, gcmIvLength, combined.size - gcmIvLength), Charsets.UTF_8)
    }
}
