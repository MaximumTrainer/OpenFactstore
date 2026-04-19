package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SonarAttestationProcessorTest {

    private val objectMapper = ObjectMapper()
    private val processor = SonarAttestationProcessor(objectMapper)

    private fun makeAttestation() = Attestation(
        trailId = UUID.randomUUID(),
        type = "sonar",
        status = AttestationStatus.PENDING
    )

    @Test
    fun `OK quality gate status sets PASSED`() {
        val json = """{"status":"OK"}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains("OK"))
    }

    @Test
    fun `ERROR quality gate status sets FAILED`() {
        val json = """{"status":"ERROR"}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertTrue(attestation.details!!.contains("ERROR"))
    }

    @Test
    fun `nested projectStatus OK sets PASSED`() {
        val json = """{"projectStatus":{"status":"OK"}}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
    }

    @Test
    fun `nested projectStatus ERROR sets FAILED`() {
        val json = """{"projectStatus":{"status":"ERROR"}}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
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

    @Test
    fun `PASSED quality gate status string sets PASSED`() {
        val json = """{"status":"PASSED"}"""

        val attestation = makeAttestation()
        processor.process(json.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
    }
}
