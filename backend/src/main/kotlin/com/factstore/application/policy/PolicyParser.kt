package com.factstore.application.policy

import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml

data class PolicyArtifactRule(
    val provenanceRequired: Boolean = false,
    val trailComplianceRequired: Boolean = false,
    val requiredAttestations: List<PolicyAttestationRule> = emptyList()
)

data class PolicyAttestationRule(
    val name: String,
    val type: String,
    val ifCondition: String? = null
)

data class ParsedPolicy(
    val schema: String? = null,
    val artifactRules: PolicyArtifactRule = PolicyArtifactRule()
)

@Component
class PolicyParser {
    private val yaml = Yaml()

    @Suppress("UNCHECKED_CAST")
    fun parse(yamlContent: String): ParsedPolicy? {
        if (yamlContent.isBlank()) return null
        return runCatching {
            val root = yaml.load<Map<String, Any>>(yamlContent) ?: return null
            val schema = root["_schema"] as? String

            val artifactsMap = root["artifacts"] as? Map<String, Any> ?: emptyMap<String, Any>()
            val provenanceMap = artifactsMap["provenance"] as? Map<String, Any> ?: emptyMap<String, Any>()
            val trailComplianceMap = artifactsMap["trail-compliance"] as? Map<String, Any> ?: emptyMap<String, Any>()
            val attestationsList = artifactsMap["attestations"] as? List<Map<String, Any>> ?: emptyList()

            val attestationRules = attestationsList.map { att ->
                PolicyAttestationRule(
                    name = att["name"] as? String ?: "",
                    type = att["type"] as? String ?: "",
                    ifCondition = att["if"] as? String
                )
            }

            ParsedPolicy(
                schema = schema,
                artifactRules = PolicyArtifactRule(
                    provenanceRequired = (provenanceMap["required"] as? Boolean) ?: false,
                    trailComplianceRequired = (trailComplianceMap["required"] as? Boolean) ?: false,
                    requiredAttestations = attestationRules
                )
            )
        }.getOrNull()
    }
}
