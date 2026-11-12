package com.factstore.application.masking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * #198 — the masking rules applied to a fact's `tool` payload at the ingestion boundary. One
 * golden case per built-in rule; the fixtures are synthetic, shaped like the real thing but not
 * working credentials.
 */
class DataMaskingTest {

    private val mapper = jacksonObjectMapper()
    private val masking = DataMasking(MaskingRuleset.builtIn())

    private fun scan(json: String, sensitive: Set<String> = emptySet()) = masking.scan(mapper.readTree(json), sensitive)

    private fun maskedText(value: String): String = scan(mapper.writeValueAsString(mapOf("log" to value))).masked["log"].asText()

    @Test
    fun `#198 the built-in ruleset is versioned`() {
        assertThat(MaskingRuleset.builtIn().version).isEqualTo("builtin-v1")
    }

    @Test
    fun `#198 each built-in rule masks its secret and keeps the surrounding text`() {
        val cases = mapOf(
            "aws-access-key-id" to "key AKIAIOSFODNN7EXAMPLE used",
            "github-token" to "token ghp_0123456789abcdefghijABCDEFGHIJ012345 used",
            "slack-token" to "token xoxb-1234567890-abcdefghijkl used",
            "private-key" to "pem -----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAKj34-----END RSA PRIVATE KEY----- used",
            "jwt" to "bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJlLXZhbHVl used",
            "factstore-api-key" to "key fsp_${"a".repeat(64)} used",
            "google-api-key" to "key AIzaSyA0123456789abcdefghijklmnopqrstuv used",
            "password-assignment" to "set password=hunter2-secret used",
            "email" to "contact alice@example.com used"
        )
        cases.forEach { (rule, text) ->
            val masked = maskedText(text)
            assertThat(masked).describedAs(rule).contains("[MASKED:$rule]")
            assertThat(masked).describedAs(rule).startsWith(text.substringBefore(' ')).endsWith(" used")
        }
    }

    @Test
    fun `#198 findings name the path and rule, never the secret`() {
        val result = scan("""{"steps":[{"out":"ok"},{"out":"AKIAIOSFODNN7EXAMPLE"}]}""")
        assertThat(result.findings).containsExactly(MaskingFinding("/steps/1/out", "aws-access-key-id"))
        assertThat(result.findings.toString()).doesNotContain("AKIAIOSFODNN7EXAMPLE")
    }

    @Test
    fun `#198 already masked values are not findings`() {
        val result = scan("""{"log":"key [MASKED:aws-access-key-id] used","user":"[MASKED:email]"}""")
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `#198 fields the schema marks sensitive are masked whole`() {
        val result = scan("""{"db":{"password":"correct horse"},"items":[{"token":"t1"},{"token":"[MASKED:sensitive-field]"}]}""",
            sensitive = setOf("/db/password", "/items/*/token"))
        assertThat(result.masked.at("/db/password").asText()).isEqualTo("[MASKED:sensitive-field]")
        assertThat(result.masked.at("/items/0/token").asText()).isEqualTo("[MASKED:sensitive-field]")
        assertThat(result.findings).containsExactlyInAnyOrder(
            MaskingFinding("/db/password", "sensitive-field"),
            MaskingFinding("/items/0/token", "sensitive-field")
        )
    }

    @Test
    fun `#198 masking is stable and leaves clean payloads untouched`() {
        val dirty = """{"log":"AKIAIOSFODNN7EXAMPLE and bob@example.org"}"""
        assertThat(scan(dirty).masked).isEqualTo(scan(dirty).masked)
        val clean = mapper.readTree("""{"tool":"junit","passed":10,"nested":{"ok":true}}""")
        val result = masking.scan(clean, emptySet())
        assertThat(result.masked).isEqualTo(clean)
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `#198 object keys are not rewritten, only string values`() {
        val result = scan("""{"AKIAIOSFODNN7EXAMPLE":1}""")
        assertThat(result.findings).isEmpty()
    }
}
