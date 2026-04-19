package com.factstore.application

import com.factstore.application.policy.PolicyParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PolicyParserTest {

    private val parser = PolicyParser()

    @Test
    fun `parse policy with provenance required`() {
        val yaml = """
            artifacts:
              provenance:
                required: true
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertTrue(result!!.artifactRules.provenanceRequired)
    }

    @Test
    fun `parse policy with trail compliance required`() {
        val yaml = """
            artifacts:
              trail-compliance:
                required: true
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertTrue(result!!.artifactRules.trailComplianceRequired)
    }

    @Test
    fun `parse policy with required attestations list`() {
        val yaml = """
            artifacts:
              attestations:
                - name: junit
                  type: junit
                - name: security-scan
                  type: snyk
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        val attestations = result!!.artifactRules.requiredAttestations
        assertEquals(2, attestations.size)
        assertEquals("junit", attestations[0].name)
        assertEquals("junit", attestations[0].type)
        assertEquals("security-scan", attestations[1].name)
        assertEquals("snyk", attestations[1].type)
    }

    @Test
    fun `parses ifCondition on attestation rule`() {
        val yaml = """
            artifacts:
              attestations:
                - name: security-scan
                  type: snyk
                  if: "flow.name == \"secure-flow\""
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        val attestation = result!!.artifactRules.requiredAttestations[0]
        assertNotNull(attestation.ifCondition)
        assertTrue(attestation.ifCondition!!.contains("flow.name"))
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
    fun `schema field is optional`() {
        val yaml = """
            artifacts:
              provenance:
                required: false
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertNull(result!!.schema)
    }

    @Test
    fun `parse full policy with schema and all fields`() {
        val yaml = """
            _schema: "https://example.com/policy/v1"
            artifacts:
              provenance:
                required: true
              trail-compliance:
                required: true
              attestations:
                - name: junit
                  type: junit
                - name: security-scan
                  type: snyk
                  if: "flow.name == \"secure-flow\""
        """.trimIndent()

        val result = parser.parse(yaml)

        assertNotNull(result)
        assertEquals("https://example.com/policy/v1", result!!.schema)
        assertTrue(result.artifactRules.provenanceRequired)
        assertTrue(result.artifactRules.trailComplianceRequired)
        assertEquals(2, result.artifactRules.requiredAttestations.size)
    }
}
