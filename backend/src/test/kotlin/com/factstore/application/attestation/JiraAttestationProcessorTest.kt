package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JiraAttestationProcessorTest {

    private val objectMapper = ObjectMapper()
    private val processor = JiraAttestationProcessor(objectMapper)

    private fun makeAttestation() = Attestation(
        trailId = UUID.randomUUID(),
        type = "jira",
        status = AttestationStatus.PENDING
    )

    @Test
    fun `valid Jira key as plain text sets PASSED`() {
        val attestation = makeAttestation()
        processor.process("PROJ-123".toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains("PROJ-123"))
    }

    @Test
    fun `invalid Jira key as plain text sets FAILED`() {
        val attestation = makeAttestation()
        processor.process("not-a-key".toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
    }

    @Test
    fun `valid Jira key in JSON sets PASSED`() {
        val json = """{"issueRef":"TICKET-456","status":"Done"}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains("TICKET-456"))
    }

    @Test
    fun `invalid Jira key in JSON sets FAILED`() {
        val json = """{"issueRef":"bad","status":"Open"}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
    }

    @Test
    fun `malformed JSON sets FAILED`() {
        val bytes = "{ not valid json !!!".toByteArray()

        val attestation = makeAttestation()
        processor.process(bytes, attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertNotNull(attestation.details)
        assertTrue(attestation.details!!.contains("error"))
    }
}
