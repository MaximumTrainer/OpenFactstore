package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class JunitAttestationProcessorTest {

    private val processor = JunitAttestationProcessor()

    private fun makeAttestation() = Attestation(
        trailId = UUID.randomUUID(),
        type = "junit",
        status = AttestationStatus.PENDING
    )

    @Test
    fun `passing JUnit XML sets status PASSED and counts tests`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="0" errors="0" skipped="1">
  <testcase name="test1"/>
</testsuite>"""

        val attestation = makeAttestation()
        processor.process(xml.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertNotNull(attestation.details)
        assertTrue(attestation.details!!.contains(""""tests":5"""))
        assertTrue(attestation.details!!.contains(""""failures":0"""))
        assertTrue(attestation.details!!.contains(""""errors":0"""))
    }

    @Test
    fun `failing JUnit XML sets status FAILED when failures gt 0`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="5" failures="2" errors="0" skipped="0">
  <testcase name="test1"/>
</testsuite>"""

        val attestation = makeAttestation()
        processor.process(xml.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertTrue(attestation.details!!.contains(""""failures":2"""))
    }

    @Test
    fun `failing JUnit XML sets status FAILED when errors gt 0`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite tests="3" failures="0" errors="1" skipped="0">
  <testcase name="test1"/>
</testsuite>"""

        val attestation = makeAttestation()
        processor.process(xml.toByteArray(), attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertTrue(attestation.details!!.contains(""""errors":1"""))
    }

    @Test
    fun `malformed XML sets status FAILED with error details`() {
        val bytes = "<<not valid xml!!>>".toByteArray()

        val attestation = makeAttestation()
        processor.process(bytes, attestation)

        assertEquals(AttestationStatus.FAILED, attestation.status)
        assertNotNull(attestation.details)
        assertTrue(attestation.details!!.contains("error"))
    }

    @Test
    fun `empty XML document returns PASSED with zero counts`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><testsuites/>"""

        val attestation = makeAttestation()
        processor.process(xml.toByteArray(), attestation)

        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertTrue(attestation.details!!.contains(""""tests":0"""))
        assertTrue(attestation.details!!.contains(""""failures":0"""))
    }
}
