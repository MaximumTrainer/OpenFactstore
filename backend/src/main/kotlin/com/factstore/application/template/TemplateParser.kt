package com.factstore.application.template

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

    private fun parseAttestations(raw: Any?): List<TemplateAttestation> {
        val list = raw as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<String, Any>>().map { att ->
            TemplateAttestation(
                name = att["name"] as? String ?: "",
                type = att["type"] as? String ?: "",
                ifCondition = att["if"] as? String
            )
        }
    }
}
