package com.factstore

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class FactstoreApplicationTest {

    @Test
    fun `handleCliArgs returns false when no recognized flags are present`() {
        assertFalse(handleCliArgs(emptyArray()))
        assertFalse(handleCliArgs(arrayOf("--server.port=9090")))
    }

    @Test
    fun `handleCliArgs returns true and prints help text for --help flag`() {
        var result = false
        val output = captureStdout { result = handleCliArgs(arrayOf("--help")) }
        assertTrue(result)
        assertTrue(output.contains("Factstore"))
        assertTrue(output.contains("--help"))
        assertTrue(output.contains("--version"))
        assertTrue(output.contains("--server.port"))
    }

    @Test
    fun `handleCliArgs returns true and prints version for --version flag`() {
        var result = false
        val output = captureStdout { result = handleCliArgs(arrayOf("--version")) }
        assertTrue(result)
        assertTrue(output.contains("Factstore"))
    }

    @Test
    fun `APP_VERSION is not blank`() {
        assertTrue(APP_VERSION.isNotBlank())
    }

    @Test
    fun `HELP_TEXT contains all required sections`() {
        assertTrue(HELP_TEXT.contains("--help"))
        assertTrue(HELP_TEXT.contains("--version"))
        assertTrue(HELP_TEXT.contains("--server.port"))
        assertTrue(HELP_TEXT.contains("swagger-ui.html"))
        assertTrue(HELP_TEXT.contains("DEPLOY.md"))
    }

    private fun captureStdout(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }
}
