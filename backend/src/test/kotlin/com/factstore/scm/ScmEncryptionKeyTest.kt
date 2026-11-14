package com.factstore.scm

import com.factstore.adapter.mock.InMemoryScmIntegrationRepository
import com.factstore.application.ScmEncryptionKeys
import com.factstore.application.ScmTokenEncryptionService
import com.factstore.application.ScmTokenReEncryption
import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** #242: the SCM token key has no usable default and is never padded or truncated. */
class ScmEncryptionKeyTest {

    private fun keyOf(bytes: Int) = Base64.getEncoder().encodeToString(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    @Test
    fun `an unset key outside a dev profile fails with an actionable message`() {
        val e = assertThrows<IllegalStateException> { ScmEncryptionKeys.parse("", devProfile = false) }
        assertThat(e.message).contains("SCM_ENCRYPTION_KEY").contains("openssl rand -base64 32")
    }

    @Test
    fun `the old literal dev key is refused in any profile`() {
        listOf(false, true).forEach { dev ->
            val e = assertThrows<IllegalStateException> { ScmEncryptionKeys.parse("default-dev-key-32chars!!!!!!", dev) }
            assertThat(e.message).contains("legacy development key")
        }
    }

    @Test
    fun `the dev key is accepted only in a dev profile`() {
        ScmEncryptionKeys.parse(ScmEncryptionKeys.DEV_KEY, devProfile = true)
        val e = assertThrows<IllegalStateException> { ScmEncryptionKeys.parse(ScmEncryptionKeys.DEV_KEY, devProfile = false) }
        assertThat(e.message).contains("development key")
    }

    @Test
    fun `an unset key in a dev profile falls back to the dev key`() {
        val key = ScmEncryptionKeys.parse("", devProfile = true)
        assertThat(key.encoded).isEqualTo(Base64.getDecoder().decode(ScmEncryptionKeys.DEV_KEY))
    }

    @ParameterizedTest
    @ValueSource(ints = [16, 31, 33])
    fun `a key that does not decode to exactly 32 bytes is rejected`(bytes: Int) {
        val e = assertThrows<IllegalStateException> { ScmEncryptionKeys.parse(keyOf(bytes), devProfile = false) }
        assertThat(e.message).contains("exactly 32 bytes").contains("$bytes")
    }

    @Test
    fun `a 32-character passphrase is not accepted as a key`() {
        // The old format: 32 ASCII characters, used raw. As base64 it decodes to 24 bytes.
        assertThrows<IllegalStateException> { ScmEncryptionKeys.parse("abcdefghijklmnopqrstuvwxyz012345", devProfile = false) }
    }

    @Test
    fun `a value that is not base64 is rejected`() {
        val e = assertThrows<IllegalStateException> { ScmEncryptionKeys.parse("not base64 at all!", devProfile = false) }
        assertThat(e.message).contains("base64")
    }

    @Test
    fun `a base64 32-byte key is used as is`() {
        val raw = ByteArray(32) { it.toByte() }
        val key = ScmEncryptionKeys.parse(Base64.getEncoder().encodeToString(raw), devProfile = false)
        assertThat(key.encoded).isEqualTo(raw)
        assertThat(key.algorithm).isEqualTo("AES")
    }

    @Test
    fun `tokens round-trip with the configured key`() {
        val service = ScmTokenEncryptionService(keyOf(32))
        val ciphertext = service.encrypt("ghp_secret")
        assertThat(ciphertext).doesNotContain("ghp_secret")
        assertThat(service.decrypt(ciphertext)).isEqualTo("ghp_secret")
    }

    /** Encrypts as the pre-#242 code did: the raw key's bytes, zero-padded or truncated to 32. */
    private fun legacyEncrypt(rawKey: String, plaintext: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(rawKey.toByteArray().copyOf(32), "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plaintext.toByteArray()))
    }

    @Test
    fun `tokens encrypted with the legacy key still decrypt when the legacy key is configured`() {
        val legacy = "default-dev-key-32chars!!!!!!"
        val old = legacyEncrypt(legacy, "ghp_old")
        val service = ScmTokenEncryptionService(keyOf(32), legacyRawKey = legacy)
        assertThat(service.decrypt(old)).isEqualTo("ghp_old")
        assertThat(service.isLegacy(old)).isTrue()
        assertThat(service.isLegacy(service.encrypt("ghp_new"))).isFalse()
    }

    @Test
    fun `without the legacy key a legacy token cannot be decrypted`() {
        val old = legacyEncrypt("default-dev-key-32chars!!!!!!", "ghp_old")
        assertThrows<Exception> { ScmTokenEncryptionService(keyOf(32)).decrypt(old) }
    }

    @Test
    fun `the one-off re-encryption moves every legacy token to the new key`() {
        val legacy = "my-old-passphrase"
        val newKey = keyOf(32)
        val migrating = ScmTokenEncryptionService(newKey, legacyRawKey = legacy)
        val repo = InMemoryScmIntegrationRepository()
        val oldOne = repo.save(ScmIntegration(orgSlug = "a", provider = ScmProvider.GITHUB, encryptedToken = legacyEncrypt(legacy, "ghp_a"), isTokenEncrypted = true))
        val oldTwo = repo.save(ScmIntegration(orgSlug = "b", provider = ScmProvider.GITLAB, encryptedToken = legacyEncrypt(legacy, "glpat_b"), isTokenEncrypted = true))
        val current = repo.save(ScmIntegration(orgSlug = "c", provider = ScmProvider.GITHUB, encryptedToken = migrating.encrypt("ghp_c"), isTokenEncrypted = true))
        val currentCiphertext = current.encryptedToken

        val result = ScmTokenReEncryption(repo, migrating).reEncryptLegacyTokens()

        assertThat(result.reEncrypted).isEqualTo(2)
        assertThat(result.alreadyCurrent).isEqualTo(1)
        assertThat(repo.findById(current.id)!!.encryptedToken).isEqualTo(currentCiphertext)
        // After the step, the new key alone reads every token.
        val newOnly = ScmTokenEncryptionService(newKey)
        assertThat(newOnly.decrypt(repo.findById(oldOne.id)!!.encryptedToken)).isEqualTo("ghp_a")
        assertThat(newOnly.decrypt(repo.findById(oldTwo.id)!!.encryptedToken)).isEqualTo("glpat_b")
        assertThat(newOnly.decrypt(repo.findById(current.id)!!.encryptedToken)).isEqualTo("ghp_c")
    }

    @Test
    fun `re-encryption without a legacy key configured is refused`() {
        val e = assertThrows<IllegalStateException> {
            ScmTokenReEncryption(InMemoryScmIntegrationRepository(), ScmTokenEncryptionService(keyOf(32))).reEncryptLegacyTokens()
        }
        assertThat(e.message).contains("SCM_LEGACY_ENCRYPTION_KEY")
    }

    // ── Startup ───────────────────────────────────────────────────────────

    private val runner = ApplicationContextRunner().withUserConfiguration(ScmTokenEncryptionService::class.java)

    @Test
    fun `the application fails to start in the prod profile without a key`() {
        runner.withInitializer { it.environment.setActiveProfiles("prod") }.run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().hasMessageContaining("SCM_ENCRYPTION_KEY")
        }
    }

    @Test
    fun `the application fails to start outside dev with the old literal key`() {
        runner.withInitializer { it.environment.setActiveProfiles("prod") }
            .withPropertyValues("factstore.scm.encryption-key=default-dev-key-32chars!!!!!!")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).rootCause().hasMessageContaining("legacy development key")
            }
    }

    @Test
    fun `the application fails to start with no profile and no key`() {
        runner.run { context -> assertThat(context).hasFailed() }
    }

    @Test
    fun `the application starts in the dev profile without a key`() {
        runner.withInitializer { it.environment.setActiveProfiles("dev") }.run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun `the application starts in prod with a proper key`() {
        runner.withInitializer { it.environment.setActiveProfiles("prod") }
            .withPropertyValues("factstore.scm.encryption-key=${keyOf(32)}")
            .run { context -> assertThat(context).hasNotFailed() }
    }
}
