package com.factstore.application

import com.factstore.application.template.TemplateAttestation
import com.factstore.application.template.TemplateParser
import com.factstore.core.domain.HubTemplate
import com.factstore.dto.ComposedTemplateResponse
import org.springframework.stereotype.Component

/**
 * Merges several flow templates into one (#162).
 *
 * The merge is a union keyed on attestation *name*: a gate required by both a service-type
 * template and a regulatory one is required once. Where the same name is required with two
 * different types the templates genuinely disagree — the first wins so the result is still
 * usable, but the disagreement is reported rather than buried.
 */
@Component
class TemplateComposer(private val templateParser: TemplateParser) {

    fun compose(templates: List<HubTemplate>): ComposedTemplateResponse {
        val trailByName = LinkedHashMap<String, TemplateAttestation>()
        val artifactsByName = LinkedHashMap<String, LinkedHashMap<String, TemplateAttestation>>()
        val conflicts = mutableListOf<String>()

        templates.forEach { template ->
            val parsed = templateParser.parse(template.yaml) ?: return@forEach

            parsed.trailAttestations.forEach { attestation ->
                merge(trailByName, attestation, template, conflicts, scope = "trail")
            }
            parsed.artifacts.forEach { artifact ->
                val target = artifactsByName.getOrPut(artifact.name) { LinkedHashMap() }
                artifact.attestations.forEach { attestation ->
                    merge(target, attestation, template, conflicts, scope = "artifact '${artifact.name}'")
                }
            }
        }

        return ComposedTemplateResponse(
            templateIds = templates.map { it.id },
            templateYaml = render(trailByName.values.toList(), artifactsByName),
            requiredAttestations = trailByName.keys.toList() +
                artifactsByName.values.flatMap { it.keys }.distinct().filterNot { it in trailByName.keys },
            conflicts = conflicts
        )
    }

    private fun merge(
        target: MutableMap<String, TemplateAttestation>,
        attestation: TemplateAttestation,
        template: HubTemplate,
        conflicts: MutableList<String>,
        scope: String
    ) {
        val existing = target[attestation.name]
        if (existing == null) {
            target[attestation.name] = attestation
            return
        }
        if (existing.type != attestation.type) {
            conflicts += "'${attestation.name}' in $scope is required as type '${existing.type}' and " +
                "as type '${attestation.type}' by ${template.id}; kept '${existing.type}'"
        }
    }

    private fun render(
        trail: List<TemplateAttestation>,
        artifacts: Map<String, Map<String, TemplateAttestation>>
    ): String = buildString {
        appendLine("version: 1")
        if (trail.isNotEmpty()) {
            appendLine("trail:")
            appendLine("  attestations:")
            trail.forEach { appendAttestation(it, indent = "    ") }
        }
        if (artifacts.isNotEmpty()) {
            appendLine("artifacts:")
            artifacts.forEach { (name, attestations) ->
                appendLine("  - name: $name")
                appendLine("    attestations:")
                attestations.values.forEach { appendAttestation(it, indent = "      ") }
            }
        }
    }

    private fun StringBuilder.appendAttestation(attestation: TemplateAttestation, indent: String) {
        appendLine("$indent- name: ${attestation.name}")
        appendLine("$indent  type: ${attestation.type}")
        // Single-quoted YAML: only ' needs escaping (as ''), so a condition's own quotes survive (#241).
        attestation.ifCondition?.let { appendLine("$indent  if: '${it.replace("'", "''")}'") }
    }
}
