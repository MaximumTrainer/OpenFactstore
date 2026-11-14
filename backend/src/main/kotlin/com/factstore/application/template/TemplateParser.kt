package com.factstore.application.template

import com.factstore.application.policy.ConditionValidation
import com.factstore.application.policy.LocatedCondition
import com.factstore.exception.BadRequestException
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml

data class TemplateAttestation(
    val name: String,
    val type: String,
    val ifCondition: String? = null
)

data class TemplateArtifact(
    val name: String,
    val attestations: List<TemplateAttestation> = emptyList()
)

data class ParsedTemplate(
    val version: Int = 1,
    val trailAttestations: List<TemplateAttestation> = emptyList(),
    val artifacts: List<TemplateArtifact> = emptyList()
)

@Component
class TemplateParser {
    private val snakeYaml = Yaml()

    @Suppress("UNCHECKED_CAST")
    fun parse(yamlContent: String): ParsedTemplate? {
        if (yamlContent.isBlank()) return null
        return runCatching {
            val root = snakeYaml.load<Map<String, Any>>(yamlContent) ?: return null
            val version = (root["version"] as? Int) ?: 1

            val trailMap = root["trail"] as? Map<String, Any> ?: emptyMap<String, Any>()
            val trailAttestations = parseAttestations(trailMap["attestations"])

            val artifactsList = (trailMap["artifacts"] as? List<*>)
                ?: (root["artifacts"] as? List<*>)
                ?: emptyList<Any>()
            val artifacts = artifactsList.filterIsInstance<Map<String, Any>>().map { artMap ->
                TemplateArtifact(
                    name = artMap["name"] as? String ?: "",
                    attestations = parseAttestations(artMap["attestations"])
                )
            }

            ParsedTemplate(version, trailAttestations, artifacts)
        }.getOrNull()
    }

    /**
     * Rejects, with `400`, a template that cannot be parsed or has an `if:` condition that is not
     * valid (#241). Call this wherever a template is accepted from a user.
     */
    fun validate(yamlContent: String) {
        if (yamlContent.isBlank()) return
        val parsed = parse(yamlContent)
            ?: throw BadRequestException("templateYaml is not a valid flow template")
        val conditions = parsed.trailAttestations.mapIndexedNotNull { i, a ->
            a.ifCondition?.let { LocatedCondition("trail.attestations[$i]", a.name, it) }
        } + parsed.artifacts.flatMapIndexed { ai, artifact ->
            artifact.attestations.mapIndexedNotNull { i, a ->
                a.ifCondition?.let { LocatedCondition("artifacts[$ai].attestations[$i]", a.name, it) }
            }
        }
        ConditionValidation.requireValid("templateYaml", conditions)
    }

    private fun parseAttestations(raw: Any?): List<TemplateAttestation> {
        val list = raw as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<String, Any>>().map { att ->
            TemplateAttestation(
                name = att["name"] as? String ?: "",
                type = att["type"] as? String ?: "",
                ifCondition = att["if"]?.toString()
            )
        }
    }
}
