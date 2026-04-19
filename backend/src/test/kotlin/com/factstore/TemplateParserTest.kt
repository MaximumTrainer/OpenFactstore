package com.factstore

import com.factstore.application.template.TemplateAttestation
import com.factstore.application.template.TemplateParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TemplateParserTest {

    private val parser = TemplateParser()

    private val fullYaml = """
version: 1
trail:
  attestations:
    - name: jira-ticket
      type: jira
artifacts:
  - name: backend
    attestations:
      - name: unit-tests
        type: junit
      - name: security-scan
        type: snyk
""".trimIndent()

    @Test
    fun `parse full YAML template`() {
        val template = parser.parse(fullYaml)
        assertNotNull(template)
        assertEquals(1, template!!.version)
        assertEquals(1, template.trailAttestations.size)
        assertEquals("jira-ticket", template.trailAttestations[0].name)
        assertEquals("jira", template.trailAttestations[0].type)
        assertEquals(1, template.artifacts.size)
        assertEquals("backend", template.artifacts[0].name)
        assertEquals(2, template.artifacts[0].attestations.size)
        assertEquals("unit-tests", template.artifacts[0].attestations[0].name)
        assertEquals("junit", template.artifacts[0].attestations[0].type)
    }

    @Test
    fun `parse YAML with trail only`() {
        val yaml = """
version: 1
trail:
  attestations:
    - name: jira-ticket
      type: jira
""".trimIndent()
        val template = parser.parse(yaml)
        assertNotNull(template)
        assertEquals(1, template!!.trailAttestations.size)
        assertTrue(template.artifacts.isEmpty())
    }

    @Test
    fun `parse YAML with artifacts only`() {
        val yaml = """
version: 1
artifacts:
  - name: backend
    attestations:
      - name: unit-tests
        type: junit
""".trimIndent()
        val template = parser.parse(yaml)
        assertNotNull(template)
        assertTrue(template!!.trailAttestations.isEmpty())
        assertEquals(1, template.artifacts.size)
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(parser.parse("   "))
    }

    @Test
    fun `parse YAML with ifCondition on attestation`() {
        val yaml = """
version: 1
trail:
  attestations:
    - name: jira-ticket
      type: jira
      if: "flow.name == \"backend\""
""".trimIndent()
        val template = parser.parse(yaml)
        assertNotNull(template)
        val att = template!!.trailAttestations[0]
        assertEquals("jira-ticket", att.name)
        assertEquals("jira", att.type)
        assertEquals("flow.name == \"backend\"", att.ifCondition)
    }
}
