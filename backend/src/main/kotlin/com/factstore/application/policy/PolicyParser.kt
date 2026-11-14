package com.factstore.application.policy

import com.factstore.exception.BadRequestException
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
                    ifCondition = att["if"]?.toString()
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

    /**
     * Rejects, with `400`, a policy that cannot be parsed or has an `if:` condition that is not
     * valid (#241).
     */
    fun validate(yamlContent: String) {
        if (yamlContent.isBlank()) return
        val parsed = parse(yamlContent) ?: throw BadRequestException("policyYaml is not a valid policy")
        ConditionValidation.requireValid(
            "policyYaml",
            parsed.artifactRules.requiredAttestations.mapIndexedNotNull { i, rule ->
                rule.ifCondition?.let { LocatedCondition("artifacts.attestations[$i]", rule.name, it) }
            }
        )
    }
}
