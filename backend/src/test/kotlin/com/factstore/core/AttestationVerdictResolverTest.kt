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
}
