package com.factstore.application.policy

import org.springframework.stereotype.Component

/**
 * Evaluates policy expressions like:
 *   ${{ flow.name == "backend-service" }}
 *   ${{ exists(flow) }}
 *   ${{ matches(artifact.name, "^backend.*") }}
 */
@Component
class PolicyExpressionEvaluator {

    data class EvaluationContext(
        val flowName: String? = null,
        val flowTags: Map<String, String> = emptyMap(),
        val artifactName: String? = null,
        val artifactFingerprint: String? = null
    )

    /**
     * Evaluates an expression string (with or without ${{ }}).
     * Returns true if the condition matches (exception applies), false otherwise.
     */
    fun evaluate(expression: String, context: EvaluationContext): Boolean {
        val expr = expression.trim()
            .removePrefix("\${{").removeSuffix("}}").trim()
        return runCatching { evaluateExpr(expr, context) }.getOrDefault(false)
    }

    private fun evaluateExpr(expr: String, ctx: EvaluationContext): Boolean {
        if (expr.startsWith("exists(") && expr.endsWith(")")) {
            val arg = expr.removePrefix("exists(").removeSuffix(")").trim()
            return when (arg) {
                "flow" -> ctx.flowName != null
                else -> false
            }
        }

        val matchesPattern = Regex("""^matches\(([^,]+),\s*"([^"]+)"\)$""")
        matchesPattern.matchEntire(expr)?.let { m ->
            val field = resolveField(m.groupValues[1].trim(), ctx)
            val pattern = m.groupValues[2]
            return field?.matches(Regex(pattern)) ?: false
        }

        val eqPattern = Regex("""^(.+?)\s*(==|!=)\s*"([^"]*)"$""")
        eqPattern.matchEntire(expr)?.let { m ->
            val field = resolveField(m.groupValues[1].trim(), ctx)
            val op = m.groupValues[2]
            val value = m.groupValues[3]
            return when (op) {
                "==" -> field == value
                "!=" -> field != value
                else -> false
            }
        }

        val inPattern = Regex("""^(.+?)\s+in\s+\[([^\]]+)\]$""")
        inPattern.matchEntire(expr)?.let { m ->
            val field = resolveField(m.groupValues[1].trim(), ctx)
            val values = m.groupValues[2].split(",").map { it.trim().trim('"') }
            return field in values
        }

        if (expr.contains(" and ")) {
            return expr.split(" and ").all { evaluateExpr(it.trim(), ctx) }
        }
        if (expr.contains(" or ")) {
            return expr.split(" or ").any { evaluateExpr(it.trim(), ctx) }
        }
        if (expr.startsWith("not ")) {
            return !evaluateExpr(expr.removePrefix("not ").trim(), ctx)
        }

        return false
    }

    private fun resolveField(fieldPath: String, ctx: EvaluationContext): String? = when (fieldPath) {
        "flow.name" -> ctx.flowName
        "artifact.name" -> ctx.artifactName
        "artifact.fingerprint" -> ctx.artifactFingerprint
        else -> if (fieldPath.startsWith("flow.tags.")) {
            ctx.flowTags[fieldPath.removePrefix("flow.tags.")]
        } else null
    }
}
