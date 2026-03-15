package com.factstore

import com.factstore.application.FlowTemplateParser
import com.factstore.core.domain.ArtifactTemplate
import com.factstore.core.domain.FlowTemplate
import com.factstore.core.domain.TemplateAttestation
import com.factstore.core.domain.TrailTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlowTemplateParserTest {

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
        val template = FlowTemplateParser.parse(fullYaml)
        assertEquals(1, template.version)
        assertEquals(1, template.trail.attestations.size)
        assertEquals("jira-ticket", template.trail.attestations[0].name)
        assertEquals("jira", template.trail.attestations[0].type)
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
        val template = FlowTemplateParser.parse(yaml)
        assertEquals(1, template.trail.attestations.size)
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
        val template = FlowTemplateParser.parse(yaml)
        assertTrue(template.trail.attestations.isEmpty())
        assertEquals(1, template.artifacts.size)
    }

    @Test
    fun `serialize FlowTemplate to YAML`() {
        val template = FlowTemplate(
            version = 1,
            trail = TrailTemplate(listOf(TemplateAttestation("jira-ticket", "jira"))),
            artifacts = listOf(ArtifactTemplate("backend", listOf(TemplateAttestation("unit-tests", "junit"))))
        )
        val yaml = FlowTemplateParser.serialize(template)
        assertTrue(yaml.contains("version: 1"))
        assertTrue(yaml.contains("jira-ticket"))
        assertTrue(yaml.contains("unit-tests"))
    }

    @Test
    fun `roundtrip parse and serialize`() {
        val original = FlowTemplateParser.parse(fullYaml)
        val serialized = FlowTemplateParser.serialize(original)
        val reparsed = FlowTemplateParser.parse(serialized)
        assertEquals(original.version, reparsed.version)
        assertEquals(original.trail.attestations.size, reparsed.trail.attestations.size)
        assertEquals(original.artifacts.size, reparsed.artifacts.size)
        assertEquals(original.artifacts[0].name, reparsed.artifacts[0].name)
        assertEquals(original.artifacts[0].attestations.size, reparsed.artifacts[0].attestations.size)
    }

    @Test
    fun `parse YAML with no version defaults to 1`() {
        val yaml = """
artifacts:
  - name: backend
    attestations:
      - name: unit-tests
        type: junit
""".trimIndent()
        val template = FlowTemplateParser.parse(yaml)
        assertEquals(1, template.version)
    }

    @Test
    fun `parse minimal empty YAML object returns empty template`() {
        val template = FlowTemplateParser.parse("{}")
        assertEquals(1, template.version)
        assertTrue(template.trail.attestations.isEmpty())
        assertTrue(template.artifacts.isEmpty())
    }

    @Test
    fun `parse YAML with trail but no attestations key`() {
        val yaml = """
version: 1
trail:
  description: no attestations here
""".trimIndent()
        val template = FlowTemplateParser.parse(yaml)
        assertTrue(template.trail.attestations.isEmpty())
    }

    @Test
    fun `parse YAML with no trail section gives empty trail attestations`() {
        val yaml = """
version: 1
artifacts:
  - name: backend
    attestations:
      - name: unit-tests
        type: junit
""".trimIndent()
        val template = FlowTemplateParser.parse(yaml)
        assertTrue(template.trail.attestations.isEmpty())
        assertEquals(1, template.artifacts.size)
    }

    @Test
    fun `serialize FlowTemplate with empty trail attestations omits trail section`() {
        val template = FlowTemplate(
            version = 1,
            trail = TrailTemplate(emptyList()),
            artifacts = listOf(ArtifactTemplate("backend", listOf(TemplateAttestation("unit-tests", "junit"))))
        )
        val yaml = FlowTemplateParser.serialize(template)
        assertFalse(yaml.contains("trail:"))
        assertTrue(yaml.contains("backend"))
    }

    @Test
    fun `serialize FlowTemplate with empty artifacts omits artifacts section`() {
        val template = FlowTemplate(
            version = 1,
            trail = TrailTemplate(listOf(TemplateAttestation("jira-ticket", "jira"))),
            artifacts = emptyList()
        )
        val yaml = FlowTemplateParser.serialize(template)
        assertFalse(yaml.contains("artifacts:"))
        assertTrue(yaml.contains("jira-ticket"))
    }

    @Test
    fun `serialize uses BLOCK style not inline flow style`() {
        val template = FlowTemplate(
            version = 1,
            trail = TrailTemplate(listOf(TemplateAttestation("jira-ticket", "jira"))),
            artifacts = listOf(ArtifactTemplate("backend", listOf(TemplateAttestation("unit-tests", "junit"))))
        )
        val yaml = FlowTemplateParser.serialize(template)
        assertFalse(yaml.contains("{name:"), "Serialized YAML should not use inline flow style")
        assertTrue(yaml.contains("\n"), "Serialized YAML should use block style with newlines")
    }

    @Test
    fun `parse then serialize preserves all attestation fields`() {
        val template = FlowTemplateParser.parse(fullYaml)
        val serialized = FlowTemplateParser.serialize(template)
        assertTrue(serialized.contains("jira-ticket"))
        assertTrue(serialized.contains("jira"))
        assertTrue(serialized.contains("unit-tests"))
        assertTrue(serialized.contains("junit"))
        assertTrue(serialized.contains("security-scan"))
        assertTrue(serialized.contains("snyk"))
    }
}
