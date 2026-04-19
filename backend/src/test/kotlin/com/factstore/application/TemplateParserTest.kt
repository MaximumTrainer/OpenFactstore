package com.factstore.application

import com.factstore.application.template.TemplateParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TemplateParserTest {

    private val parser = TemplateParser()

    @Test
    fun `parse full YAML with trail and artifact attestations`() {
        val yaml = """
            version: 2
            trail:
              attestations:
                - name: unit-tests
                  type: junit
                - name: security-scan
                  type: snyk
              artifacts:
                - name: backend
                  attestations:
                    - name: image-scan
                      type: trivy
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertEquals(2, result!!.version)
        assertEquals(2, result.trailAttestations.size)
        assertEquals("unit-tests", result.trailAttestations[0].name)
        assertEquals("junit", result.trailAttestations[0].type)
        assertEquals(1, result.artifacts.size)
        assertEquals("backend", result.artifacts[0].name)
        assertEquals(1, result.artifacts[0].attestations.size)
        assertEquals("image-scan", result.artifacts[0].attestations[0].name)
    }

    @Test
    fun `parse YAML with trail attestations only`() {
        val yaml = """
            version: 1
            trail:
              attestations:
                - name: lint
                  type: generic
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertEquals(1, result!!.trailAttestations.size)
        assertEquals("lint", result.trailAttestations[0].name)
        assertTrue(result.artifacts.isEmpty())
    }

    @Test
    fun `parse YAML with artifact attestations only`() {
        val yaml = """
            version: 1
            artifacts:
              - name: frontend
                attestations:
                  - name: e2e-tests
                    type: junit
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertTrue(result!!.trailAttestations.isEmpty())
        assertEquals(1, result.artifacts.size)
        assertEquals("frontend", result.artifacts[0].name)
        assertEquals("e2e-tests", result.artifacts[0].attestations[0].name)
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("\n\t"))
    }

    @Test
    fun `returns null for invalid YAML`() {
        val result = parser.parse("{ not valid yaml: [unclosed")
        assertNull(result)
    }

    @Test
    fun `defaults to version 1 when version field missing`() {
        val yaml = """
            trail:
              attestations:
                - name: test
                  type: junit
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertEquals(1, result!!.version)
    }

    @Test
    fun `parses ifCondition on attestation`() {
        val yaml = """
            version: 1
            trail:
              attestations:
                - name: security-scan
                  type: snyk
                  if: "flow.name == \"secure-flow\""
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        val attestation = result!!.trailAttestations[0]
        assertEquals("security-scan", attestation.name)
        assertNotNull(attestation.ifCondition)
        assertTrue(attestation.ifCondition!!.contains("flow.name"))
    }
}
