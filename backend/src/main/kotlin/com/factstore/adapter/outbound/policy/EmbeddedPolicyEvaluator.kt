package com.factstore.adapter.outbound.policy

import com.factstore.core.port.outbound.IPolicyEvaluator
import com.factstore.core.port.outbound.PolicyEvaluationResult
import com.factstore.core.port.outbound.PolicyInput
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Simplified Rego evaluator for embedded mode. Use external OPA server for full Rego support.
 *
 * Supports a subset of Rego patterns:
 * - `input.approvalStatus == "APPROVED"` → deny when approvalStatus is not "APPROVED"
 * - `input.criticalVulnerabilities == 0` → deny when extraData contains criticalVulnerabilities > 0
 */
@Primary
@Component
class EmbeddedPolicyEvaluator : IPolicyEvaluator {

    override fun evaluate(input: PolicyInput, regoContent: String): PolicyEvaluationResult {
        val denyReasons = mutableListOf<String>()

        if (regoContent.contains("""input.approvalStatus == "APPROVED"""")) {
            if (input.approvalStatus != "APPROVED") {
                denyReasons.add("approvalStatus is not APPROVED (got: ${input.approvalStatus})")
            }
        }

        if (regoContent.contains("input.criticalVulnerabilities == 0")) {
            val critical = input.extraData["criticalVulnerabilities"]
            if (critical != null && (critical as? Number)?.toInt() ?: 0 > 0) {
                denyReasons.add("criticalVulnerabilities is not 0 (got: $critical)")
            }
        }

        return if (denyReasons.isEmpty()) {
            PolicyEvaluationResult(allow = true)
        } else {
            PolicyEvaluationResult(allow = false, denyReasons = denyReasons)
        }
    }
}
