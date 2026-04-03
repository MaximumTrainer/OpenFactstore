package com.factstore.adapter.outbound.policy

import com.factstore.core.port.outbound.IPolicyEvaluator
import com.factstore.core.port.outbound.PolicyEvaluationResult
import com.factstore.core.port.outbound.PolicyInput
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Embedded Rego policy evaluator supporting a meaningful subset of OPA patterns.
 *
 * Supported patterns:
 * - `deny[msg] { <conditions>; msg := "reason" }` — deny rules with message extraction
 * - `allow { <conditions> }` — explicit allow rules
 * - `default allow = false` — require at least one allow rule to pass
 * - Conditions: `input.field == "value"`, `input.field != "value"`
 * - Numeric:    `input.field > N`, `input.field >= N`, `input.field < N`, `input.field <= N`
 * - Existence:  `input.field` (truthy check), `not input.field` (falsy check)
 * - Count:      `count(input.field) > N` / `>= N` / `== N`
 * - Multiple conditions per block are evaluated with implicit logical AND
 *
 * For full Rego support (imports, virtual documents, comprehensions, partial rules),
 * set `opa.mode=external` and configure `opa.external-url` to point to a running OPA server.
 */
@Primary
@Component
class EmbeddedPolicyEvaluator : IPolicyEvaluator {

    override fun evaluate(input: PolicyInput, regoContent: String): PolicyEvaluationResult {
        val ctx = buildContext(input)

        val denyReasons = evaluateDenyRules(regoContent, ctx)
        if (denyReasons.isNotEmpty()) {
            return PolicyEvaluationResult(allow = false, denyReasons = denyReasons)
        }

        // If policy declares `default allow = false`, require an allow rule to match
        if (regoContent.contains(Regex("""default\s+allow\s*[:=]\s*false"""))) {
            val allowBlocks = extractRuleBlocks("allow", regoContent)
            val allowed = allowBlocks.any { conditions -> evaluateConditions(conditions, ctx) }
            return if (allowed) {
                PolicyEvaluationResult(allow = true)
            } else {
                PolicyEvaluationResult(allow = false, denyReasons = listOf("No allow rule was satisfied"))
            }
        }

        return PolicyEvaluationResult(allow = true)
    }

    // ── Context building ─────────────────────────────────────────────────────

    private fun buildContext(input: PolicyInput): Map<String, Any?> {
        val base: Map<String, Any?> = mapOf(
            "artifactName" to input.artifactName,
            "artifactVersion" to input.artifactVersion,
            "environment" to input.environment,
            "attestations" to input.attestations,
            "approvalStatus" to input.approvalStatus
        )
        return base + input.extraData
    }

    // ── Deny rule evaluation ─────────────────────────────────────────────────

    private fun evaluateDenyRules(regoContent: String, ctx: Map<String, Any?>): List<String> {
        val reasons = mutableListOf<String>()
        for (block in extractRuleBlocks("""deny\[msg\]""", regoContent)) {
            val msgLine = block.find { it.trimStart().startsWith("msg") }
            val conditions = block.filter { it != msgLine }
            if (evaluateConditions(conditions, ctx)) {
                reasons.add(extractMessageValue(msgLine) ?: "Policy deny rule matched")
            }
        }
        return reasons
    }

    // ── Rule block extraction ────────────────────────────────────────────────

    /**
     * Extracts all `<ruleName> { ... }` blocks from Rego content.
     * Returns each block as a list of trimmed condition strings.
     */
    private fun extractRuleBlocks(ruleNamePattern: String, regoContent: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        val regex = Regex("""$ruleNamePattern\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in regex.findAll(regoContent)) {
            val body = match.groupValues[1]
            val conditions = body.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
            if (conditions.isNotEmpty()) blocks.add(conditions)
        }
        return blocks
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    private fun evaluateConditions(conditions: List<String>, ctx: Map<String, Any?>): Boolean =
        conditions.all { evaluateSingleCondition(it, ctx) }

    private fun evaluateSingleCondition(condition: String, ctx: Map<String, Any?>): Boolean {
        val c = condition.trim().removeSuffix(";").trim()
        return when {
            // Skip message assignment lines (they are not conditions)
            c.startsWith("msg") && c.contains(":=") -> true

            // count(input.X) comparisons
            c.startsWith("count(") -> evaluateCountCondition(c, ctx)

            // not input.X — negated existence check (must precede general input.X check)
            c.startsWith("not ") -> !evaluateSingleCondition(c.removePrefix("not ").trim(), ctx)

            // input.X operator value
            c.contains(Regex("""\binput\.\w+\b""")) -> evaluateInputComparison(c, ctx)

            // Fallback: unknown/unsupported pattern → pass (don't block on unsupported syntax)
            else -> true
        }
    }

    private fun evaluateInputComparison(condition: String, ctx: Map<String, Any?>): Boolean {
        val comparisonRegex = Regex("""input\.(\w+)\s*(==|!=|>=|<=|>|<)\s*(.+)""")
        val existenceRegex = Regex("""input\.(\w+)""")

        val compMatch = comparisonRegex.find(condition)
        if (compMatch != null) {
            val field = compMatch.groupValues[1]
            val op = compMatch.groupValues[2]
            val rhsRaw = compMatch.groupValues[3].trim().removeSuffix(";").trim()
            val actual = ctx[field]
            return compareValues(actual, op, rhsRaw)
        }

        // Bare field existence: `input.field`
        val existMatch = existenceRegex.find(condition)
        if (existMatch != null) {
            val field = existMatch.groupValues[1]
            val value = ctx[field]
            return value != null && value != false && value != "" && value != 0
        }

        return true
    }

    private fun evaluateCountCondition(condition: String, ctx: Map<String, Any?>): Boolean {
        val regex = Regex("""count\(input\.(\w+)\)\s*(==|!=|>=|<=|>|<)\s*(\d+)""")
        val match = regex.find(condition) ?: return true
        val field = match.groupValues[1]
        val op = match.groupValues[2]
        val threshold = match.groupValues[3].toLongOrNull() ?: return true
        val value = ctx[field]
        val count = when (value) {
            is Collection<*> -> value.size.toLong()
            is Array<*> -> value.size.toLong()
            is String -> value.length.toLong()
            null -> 0L
            else -> return true
        }
        return compareNumeric(count.toDouble(), op, threshold.toDouble())
    }

    // ── Value comparison ─────────────────────────────────────────────────────

    private fun compareValues(actual: Any?, op: String, rhsRaw: String): Boolean {
        if (actual == null) return op == "!="

        // String literal (quoted)
        if (rhsRaw.startsWith('"') && rhsRaw.endsWith('"')) {
            val rhsStr = rhsRaw.removeSurrounding("\"")
            val actualStr = actual.toString()
            return when (op) {
                "==" -> actualStr == rhsStr
                "!=" -> actualStr != rhsStr
                else -> false
            }
        }

        // Numeric literal
        val rhsNum = rhsRaw.toDoubleOrNull()
        if (rhsNum != null) {
            val actualNum = when (actual) {
                is Number -> actual.toDouble()
                is String -> actual.toDoubleOrNull() ?: return false
                else -> return false
            }
            return compareNumeric(actualNum, op, rhsNum)
        }

        // Boolean literal
        if (rhsRaw == "true" || rhsRaw == "false") {
            val rhsBool = rhsRaw.toBoolean()
            val actualBool = when (actual) {
                is Boolean -> actual
                is String -> actual.toBooleanStrictOrNull()
                else -> null
            } ?: return false
            return when (op) {
                "==" -> actualBool == rhsBool
                "!=" -> actualBool != rhsBool
                else -> false
            }
        }

        // Unquoted string (e.g. enum values like APPROVED, PENDING)
        return when (op) {
            "==" -> actual.toString() == rhsRaw
            "!=" -> actual.toString() != rhsRaw
            else -> false
        }
    }

    private fun compareNumeric(actual: Double, op: String, rhs: Double): Boolean = when (op) {
        "==" -> actual == rhs
        "!=" -> actual != rhs
        ">" -> actual > rhs
        ">=" -> actual >= rhs
        "<" -> actual < rhs
        "<=" -> actual <= rhs
        else -> false
    }

    // ── Message extraction ───────────────────────────────────────────────────

    private fun extractMessageValue(msgLine: String?): String? {
        if (msgLine == null) return null
        val quoted = Regex(""""([^"]+)"""").find(msgLine)
        if (quoted != null) return quoted.groupValues[1]
        val afterAssign = Regex(""":=\s*(.+)""").find(msgLine)
        return afterAssign?.groupValues?.get(1)?.trim()?.removeSuffix(";")
    }
}

