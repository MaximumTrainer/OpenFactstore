package com.factstore.application.policy

import com.factstore.exception.BadRequestException

/** One `if:` condition found in an uploaded document, with where it came from. */
data class LocatedCondition(
    /** Path of the rule in the YAML document, e.g. `artifacts[0].attestations[1]`. */
    val location: String,
    /** The attestation name the rule declares. */
    val ruleName: String,
    val condition: String
)

/**
 * Checks every `if:` condition of an uploaded template or policy (#241) and rejects the upload
 * with `400` naming the first offending rule, its location and the error position.
 */
object ConditionValidation {
    private val evaluator = PolicyExpressionEvaluator()

    fun requireValid(document: String, conditions: List<LocatedCondition>) {
        conditions.forEach { c ->
            try {
                evaluator.validate(c.condition)
            } catch (e: ConditionException) {
                throw BadRequestException(
                    "$document: invalid if: condition on attestation '${c.ruleName}' at ${c.location} " +
                        "(position ${e.position}): ${e.message}"
                )
            }
        }
    }
}
