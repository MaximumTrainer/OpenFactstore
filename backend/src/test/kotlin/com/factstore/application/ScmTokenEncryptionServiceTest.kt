package com.factstore.application

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScmTokenEncryptionServiceTest {

    private val service = ScmTokenEncryptionService("test-key-that-is-32-characters!!")

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val plaintext = "my-secret-token"
        val encrypted = service.encrypt(plaintext)
        assertEquals(plaintext, service.decrypt(encrypted))
    }

    @Test
    fun `encrypt returns different value each call (IV randomness)`() {
        val plaintext = "same-token"
        val first = service.encrypt(plaintext)
        val second = service.encrypt(plaintext)
        assertNotEquals(first, second)
    }

    @Test
    fun `encrypt and decrypt with short key is padded to 32 bytes`() {
        val shortKeyService = ScmTokenEncryptionService("short")
        val plaintext = "token-with-short-key"
        assertEquals(plaintext, shortKeyService.decrypt(shortKeyService.encrypt(plaintext)))
    }

    @Test
    fun `encrypt and decrypt empty string`() {
        val encrypted = service.encrypt("")
        assertEquals("", service.decrypt(encrypted))
    }

    @Test
    fun `encrypt and decrypt long token`() {
        val longToken = "a".repeat(10_000)
        assertEquals(longToken, service.decrypt(service.encrypt(longToken)))
    }

    @Test
    fun `decrypt invalid base64 throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.decrypt("not-valid-base64!!!")
        }
    }
}
