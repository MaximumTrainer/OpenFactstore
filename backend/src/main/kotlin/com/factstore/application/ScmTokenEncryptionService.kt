package com.factstore.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class ScmTokenEncryptionService(
    @Value("\${factstore.scm.encryption-key:default-dev-key-32chars!!!!!!}")
    private val rawKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val GCM_IV_LENGTH = 12
    private val GCM_TAG_LENGTH = 128

    private fun deriveKey(): SecretKey {
        val keyBytes = rawKey.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(ciphertext: String): String {
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
