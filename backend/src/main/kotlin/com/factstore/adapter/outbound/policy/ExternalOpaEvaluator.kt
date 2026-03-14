package com.factstore.adapter.outbound.policy

import com.factstore.core.port.outbound.IPolicyEvaluator
import com.factstore.core.port.outbound.PolicyEvaluationResult
import com.factstore.core.port.outbound.PolicyInput
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@ConditionalOnProperty("opa.mode", havingValue = "external")
@Component
class ExternalOpaEvaluator(
    private val restTemplate: RestTemplate,
    @Value("\${opa.external-url:http://localhost:8181}") private val opaUrl: String
) : IPolicyEvaluator {

    private val log = LoggerFactory.getLogger(ExternalOpaEvaluator::class.java)

    override fun evaluate(input: PolicyInput, regoContent: String): PolicyEvaluationResult {
        val requestBody = mapOf("input" to (mapOf(
            "artifactName" to input.artifactName,
            "artifactVersion" to input.artifactVersion,
            "environment" to input.environment,
            "attestations" to input.attestations,
            "approvalStatus" to input.approvalStatus
        ) + input.extraData))

        return try {
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                "$opaUrl/v1/data/compliance",
                requestBody,
                Map::class.java
            ) as? Map<String, Any>

            val result = response?.get("result") as? Map<String, Any>
            val allow = result?.get("allow") as? Boolean ?: false
            @Suppress("UNCHECKED_CAST")
            val deny = result?.get("deny") as? List<String> ?: emptyList()

            PolicyEvaluationResult(allow = allow, denyReasons = deny)
        } catch (ex: Exception) {
            log.error("Failed to call external OPA at $opaUrl: ${ex.message}")
            PolicyEvaluationResult(allow = false, denyReasons = listOf("OPA evaluation failed: ${ex.message}"))
        }
    }
}
