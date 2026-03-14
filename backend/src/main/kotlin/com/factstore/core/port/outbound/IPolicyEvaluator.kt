package com.factstore.core.port.outbound

data class PolicyInput(
    val artifactName: String,
    val artifactVersion: String? = null,
    val environment: String? = null,
    val attestations: List<Map<String, String>> = emptyList(),
    val approvalStatus: String? = null,
    val extraData: Map<String, Any> = emptyMap()
)

data class PolicyEvaluationResult(
    val allow: Boolean,
    val denyReasons: List<String> = emptyList()
)

interface IPolicyEvaluator {
    fun evaluate(input: PolicyInput, regoContent: String): PolicyEvaluationResult
}
