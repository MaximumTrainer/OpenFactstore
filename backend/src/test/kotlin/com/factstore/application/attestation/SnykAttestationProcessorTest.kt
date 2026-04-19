package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SnykAttestationProcessorTest {

    private val objectMapper = ObjectMapper()
    private val processor = SnykAttestationProcessor(objectMapper)

    private fun makeAttestation() = Attestation(
        trailId = UUID.randomUUID(),
        type = "snyk",
        status = AttestationStatus.PENDING
    )

    @Test
    fun `zero vulnerabilities in SARIF format sets PASSED`() {
        val sarif = """{"runs":[{"results":[]}]}"""

        val attestation = makeAttestation()
        processor.process(sarif.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains(""""vulnerabilities":0"""))
    }

    @Test
    fun `vulnerabilities in SARIF format sets FAILED`() {
        val sarif = """{"runs":[{"results":[{"ruleId":"VULN-1"},{"ruleId":"VULN-2"}]}]}"""

        val attestation = makeAttestation()
        processor.process(sarif.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertTrue(attestation.details!!.contains(""""vulnerabilities":2"""))
    }

    @Test
    fun `zero vulnerabilities in Snyk JSON format sets PASSED`() {
        val snykJson = """{"vulnerabilities":[]}"""

        val attestation = makeAttestation()
        processor.process(snykJson.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains(""""vulnerabilities":0"""))
    }

    @Test
    fun `vulnerabilities in Snyk JSON format sets FAILED`() {
        val snykJson = """{"vulnerabilities":[{"id":"SNYK-001"}]}"""

        val attestation = makeAttestation()
        processor.process(snykJson.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertTrue(attestation.details!!.contains(""""vulnerabilities":1"""))
    }

    @Test
    fun `malformed JSON sets FAILED with error details`() {
        val bytes = "{ not valid json !!!".toByteArray()

        val attestation = makeAttestation()
        processor.process(bytes, attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertNotNull(attestation.details)
        assertTrue(attestation.details!!.contains("error"))
    }
}
