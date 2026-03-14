package com.factstore.application

import com.factstore.core.domain.ArtifactTemplate
import com.factstore.core.domain.FlowTemplate
import com.factstore.core.domain.TemplateAttestation
import com.factstore.core.domain.TrailTemplate
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

object FlowTemplateParser {

    fun parse(yaml: String): FlowTemplate {
        val snakeYaml = Yaml()
        val map = snakeYaml.load<Map<String, Any>>(yaml) ?: emptyMap()
        val version = (map["version"] as? Int) ?: 1
        val trailMap = map["trail"] as? Map<String, Any> ?: emptyMap()
        val trailAttestations = parseAttestations(trailMap["attestations"])
        val artifactsList = (map["artifacts"] as? List<*>) ?: emptyList<Any>()
        val artifacts = artifactsList.filterIsInstance<Map<String, Any>>().map { artMap ->
            ArtifactTemplate(
                name = artMap["name"] as String,
                attestations = parseAttestations(artMap["attestations"])
            )
        }
        return FlowTemplate(version, TrailTemplate(trailAttestations), artifacts)
    }

    private fun parseAttestations(raw: Any?): List<TemplateAttestation> {
        val list = raw as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<String, Any>>()
            .map { TemplateAttestation(name = it["name"] as String, type = it["type"] as String) }
    }

    fun serialize(template: FlowTemplate): String {
        val map = mutableMapOf<String, Any>()
        map["version"] = template.version
        if (template.trail.attestations.isNotEmpty()) {
            map["trail"] = mapOf(
                "attestations" to template.trail.attestations.map { mapOf("name" to it.name, "type" to it.type) }
            )
        }
        if (template.artifacts.isNotEmpty()) {
            map["artifacts"] = template.artifacts.map { art ->
                mapOf(
                    "name" to art.name,
                    "attestations" to art.attestations.map { mapOf("name" to it.name, "type" to it.type) }
                )
            }
        }
        val options = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK }
        return Yaml(options).dump(map)
    }
}
