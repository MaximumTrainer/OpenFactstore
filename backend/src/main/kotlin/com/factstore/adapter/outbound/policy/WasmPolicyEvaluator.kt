package com.factstore.adapter.outbound.policy

import com.factstore.core.port.outbound.IPolicyEvaluator
import com.factstore.core.port.outbound.PolicyEvaluationResult
import com.factstore.core.port.outbound.PolicyInput
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component("wasmPolicyEvaluator")
@ConditionalOnProperty(name = ["factstore.policy.evaluator"], havingValue = "wasm")
class WasmPolicyEvaluator(private val objectMapper: ObjectMapper) : IPolicyEvaluator {
    private val log = LoggerFactory.getLogger(WasmPolicyEvaluator::class.java)

    override fun evaluate(input: PolicyInput, regoContent: String): PolicyEvaluationResult {
        // WASM stub: parse regoContent as JSON policy spec { "allow": true/false, "reason": "..." }
        // A real implementation would execute .wasm bytes via Wasmtime JVM bindings.
        return try {
            @Suppress("UNCHECKED_CAST")
            val spec = objectMapper.readValue(regoContent, Map::class.java) as Map<String, Any>
            val allow = spec["allow"] as? Boolean ?: false
            val reason = spec["reason"] as? String ?: "WASM policy evaluated"
            PolicyEvaluationResult(
                allow = allow,
                denyReasons = if (!allow) listOf(reason) else emptyList()
            )
        } catch (e: Exception) {
            log.warn("WASM policy evaluation failed: {}", e.message)
            PolicyEvaluationResult(allow = false, denyReasons = listOf("WASM evaluation error: ${e.message}"))
        }
    }
}
