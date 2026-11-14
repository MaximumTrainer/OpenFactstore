package com.factstore.core

import com.factstore.adapter.mock.InMemoryCustomAttestationTypeRepository
import com.factstore.application.attestation.AttestationVerdictResolver
import com.factstore.application.attestation.JunitAttestationProcessor
import com.factstore.application.attestation.SnykAttestationProcessor
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.CustomAttestationType
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** #240: the server, not the caller, decides whether evidence passed. */
class AttestationVerdictResolverTest {

    private lateinit var customTypes: InMemoryCustomAttestationTypeRepository
    private lateinit var resolver: AttestationVerdictResolver

    @BeforeEach
    fun setUp() {
        customTypes = InMemoryCustomAttestationTypeRepository()
        val mapper = ObjectMapper()
        resolver = AttestationVerdictResolver(
            listOf(JunitAttestationProcessor(), SnykAttestationProcessor(mapper)),
            customTypes,
            mapper
        )
    }

    @Test
    fun `a claimed PASSED for a type with a processor is ignored until evidence arrives`() {
        listOf("junit", "snyk", "JUnit").forEach { type ->
            val verdict = resolver.resolve(type, AttestationStatus.PASSED, attestationData = null)
            assertEquals(AttestationStatus.PENDING, verdict.status, type)
            assertFalse(verdict.selfAsserted, type)
        }
    }

    @Test
    fun `a claimed FAILED for an evaluated type is kept since failing closed is safe`() {
        val verdict = resolver.resolve("junit", AttestationStatus.FAILED, attestationData = null)
        assertEquals(AttestationStatus.FAILED, verdict.status)
    }

    @Test
    fun `a custom type with a jq expression uses the jq result and ignores the claim`() {
        customTypes.save(CustomAttestationType(name = "release-check", jqExpression = ".approved == true"))

        val failing = resolver.resolve("release-check", AttestationStatus.PASSED, """{"approved": false}""")
        assertEquals(AttestationStatus.FAILED, failing.status)
        assertFalse(failing.selfAsserted)

        val passing = resolver.resolve("release-check", AttestationStatus.FAILED, """{"approved": true}""")
        assertEquals(AttestationStatus.PASSED, passing.status)
        assertFalse(passing.selfAsserted)
    }

    @Test
    fun `a jq type without data or with data the expression cannot read stays PENDING`() {
        customTypes.save(CustomAttestationType(name = "release-check", jqExpression = ".approved == true"))
        assertEquals(AttestationStatus.PENDING, resolver.resolve("release-check", AttestationStatus.PASSED, null).status)
        assertEquals(AttestationStatus.PENDING, resolver.resolve("release-check", AttestationStatus.PASSED, """{"other": 1}""").status)
        assertEquals(AttestationStatus.PENDING, resolver.resolve("release-check", AttestationStatus.PASSED, "not json").status)
    }

    @Test
    fun `a type with no evaluator keeps the claim and marks it self-asserted`() {
        val verdict = resolver.resolve("manual-review", AttestationStatus.PASSED, null)
        assertEquals(AttestationStatus.PASSED, verdict.status)
        assertTrue(verdict.selfAsserted)
    }

    @Test
    fun `a PENDING claim is not self-asserted`() {
        assertFalse(resolver.resolve("manual-review", AttestationStatus.PENDING, null).selfAsserted)
    }

    @Test
    fun `a custom type without a jq expression is self-asserted`() {
        customTypes.save(CustomAttestationType(name = "sign-off"))
        val verdict = resolver.resolve("sign-off", AttestationStatus.PASSED, """{"by": "alice"}""")
        assertEquals(AttestationStatus.PASSED, verdict.status)
        assertTrue(verdict.selfAsserted)
    }

    private fun jq(expression: String, data: String): AttestationStatus {
        val name = "t-${System.nanoTime()}"
        customTypes.save(CustomAttestationType(name = name, jqExpression = expression))
        return resolver.resolve(name, AttestationStatus.PENDING, data).status
    }

    @Test
    fun `jq field-only expressions use the field's truth`() {
        assertEquals(AttestationStatus.PASSED, jq(".ok", """{"ok": true}"""))
        assertEquals(AttestationStatus.FAILED, jq(".ok", """{"ok": false}"""))
        assertEquals(AttestationStatus.FAILED, jq(".ok", """{"ok": null}"""))
        assertEquals(AttestationStatus.PASSED, jq(".ok", """{"ok": "yes"}"""))
        assertEquals(AttestationStatus.PENDING, jq(".ok", """{"other": true}"""))
    }

    @Test
    fun `jq comparisons cover strings, false and nested paths`() {
        assertEquals(AttestationStatus.PASSED, jq(""".result.state == "green"""", """{"result": {"state": "green"}}"""))
        assertEquals(AttestationStatus.FAILED, jq(""".result.state == "green"""", """{"result": {"state": "red"}}"""))
        assertEquals(AttestationStatus.PASSED, jq(".blocked == false", """{"blocked": false}"""))
        assertEquals(AttestationStatus.FAILED, jq(".blocked == false", """{"blocked": true}"""))
        assertEquals(AttestationStatus.PASSED, jq(".count == 3", """{"count": 3}"""))
        assertEquals(AttestationStatus.PENDING, jq(".a.b == 1", """{"a": {}}"""))
    }

    @Test
    fun `hasProcessor matches type names case-insensitively`() {
        assertTrue(resolver.hasProcessor("junit"))
        assertTrue(resolver.hasProcessor("SNYK"))
        assertFalse(resolver.hasProcessor("manual-review"))
    }

    @Test
    fun `applyEvidence lets the processor decide and clears self-assertion`() {
        val att = com.factstore.core.domain.Attestation(
            trailId = java.util.UUID.randomUUID(), type = "junit", status = AttestationStatus.PENDING, selfAsserted = true
        )
        val ran = resolver.applyEvidence(att, """<testsuite tests="1" failures="1" errors="0"/>""".toByteArray())
        assertTrue(ran)
        assertEquals(AttestationStatus.FAILED, att.status)
        assertFalse(att.selfAsserted)
    }

    @Test
    fun `applyEvidence does nothing for a type without a processor`() {
        val att = com.factstore.core.domain.Attestation(
            trailId = java.util.UUID.randomUUID(), type = "manual-review", status = AttestationStatus.PASSED, selfAsserted = true
        )
        assertFalse(resolver.applyEvidence(att, "anything".toByteArray()))
        assertEquals(AttestationStatus.PASSED, att.status)
        assertTrue(att.selfAsserted)
    }

    @Test
    fun `a FAILED claim for a jq type is still decided by the expression`() {
        customTypes.save(CustomAttestationType(name = "chk", jqExpression = ".ok"))
        assertEquals(AttestationStatus.PENDING, resolver.resolve("chk", AttestationStatus.FAILED, null).status)
    }
}
