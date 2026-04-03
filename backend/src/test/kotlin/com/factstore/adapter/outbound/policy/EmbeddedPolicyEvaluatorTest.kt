package com.factstore.adapter.outbound.policy

import com.factstore.core.port.outbound.PolicyInput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmbeddedPolicyEvaluatorTest {

    private val evaluator = EmbeddedPolicyEvaluator()

    private fun input(
        artifactName: String = "my-service",
        approvalStatus: String? = null,
        environment: String? = null,
        extraData: Map<String, Any> = emptyMap()
    ) = PolicyInput(
        artifactName = artifactName,
        approvalStatus = approvalStatus,
        environment = environment,
        extraData = extraData
    )

    // ── default allow (no deny rules, no default=false) ─────────────────────

    @Test
    fun `empty policy allows everything`() {
        val result = evaluator.evaluate(input(), "package compliance")
        assertTrue(result.allow)
        assertTrue(result.denyReasons.isEmpty())
    }

    // ── deny[msg] rules ──────────────────────────────────────────────────────

    @Test
    fun `deny rule fires when condition matches`() {
        val rego = """
            package compliance
            deny[msg] {
                input.approvalStatus != "APPROVED"
                msg := "Approval required"
            }
        """.trimIndent()
        val result = evaluator.evaluate(input(approvalStatus = "PENDING"), rego)
        assertFalse(result.allow)
        assertEquals(listOf("Approval required"), result.denyReasons)
    }

    @Test
    fun `deny rule does not fire when condition is false`() {
        val rego = """
            package compliance
            deny[msg] {
                input.approvalStatus != "APPROVED"
                msg := "Approval required"
            }
        """.trimIndent()
        val result = evaluator.evaluate(input(approvalStatus = "APPROVED"), rego)
        assertTrue(result.allow)
    }

    @Test
    fun `multiple deny rules — all matching return all reasons`() {
        val rego = """
            package compliance
            deny[msg] {
                input.approvalStatus != "APPROVED"
                msg := "Approval required"
            }
            deny[msg] {
                input.environment == "production"
                msg := "Production deployments need extra sign-off"
            }
        """.trimIndent()
        val result = evaluator.evaluate(
            input(approvalStatus = "PENDING", environment = "production"), rego
        )
        assertFalse(result.allow)
        assertEquals(2, result.denyReasons.size)
        assertTrue(result.denyReasons.contains("Approval required"))
        assertTrue(result.denyReasons.contains("Production deployments need extra sign-off"))
    }

    @Test
    fun `multiple deny rules — only matching ones are included`() {
        val rego = """
            package compliance
            deny[msg] {
                input.approvalStatus != "APPROVED"
                msg := "Approval required"
            }
            deny[msg] {
                input.environment == "production"
                msg := "Production sign-off needed"
            }
        """.trimIndent()
        val result = evaluator.evaluate(
            input(approvalStatus = "PENDING", environment = "staging"), rego
        )
        assertFalse(result.allow)
        assertEquals(listOf("Approval required"), result.denyReasons)
    }

    // ── Numeric comparisons ──────────────────────────────────────────────────

    @Test
    fun `deny on numeric greater-than condition`() {
        val rego = """
            package compliance
            deny[msg] {
                input.criticalVulnerabilities > 0
                msg := "Critical vulnerabilities detected"
            }
        """.trimIndent()
        val result = evaluator.evaluate(
            input(extraData = mapOf("criticalVulnerabilities" to 3)), rego
        )
        assertFalse(result.allow)
        assertTrue(result.denyReasons.contains("Critical vulnerabilities detected"))
    }

    @Test
    fun `deny on numeric greater-than does not fire when zero`() {
        val rego = """
            package compliance
            deny[msg] {
                input.criticalVulnerabilities > 0
                msg := "Critical vulnerabilities detected"
            }
        """.trimIndent()
        val result = evaluator.evaluate(
            input(extraData = mapOf("criticalVulnerabilities" to 0)), rego
        )
        assertTrue(result.allow)
    }

    @Test
    fun `deny on numeric less-than-or-equal condition`() {
        val rego = """
            package compliance
            deny[msg] {
                input.coveragePercent <= 80
                msg := "Code coverage below threshold"
            }
        """.trimIndent()
        val resultBelow = evaluator.evaluate(input(extraData = mapOf("coveragePercent" to 75)), rego)
        assertFalse(resultBelow.allow)

        val resultAbove = evaluator.evaluate(input(extraData = mapOf("coveragePercent" to 90)), rego)
        assertTrue(resultAbove.allow)
    }

    @Test
    fun `deny on numeric equality`() {
        val rego = """
            package compliance
            deny[msg] {
                input.approvedCount == 0
                msg := "At least one approval is required"
            }
        """.trimIndent()
        assertFalse(evaluator.evaluate(input(extraData = mapOf("approvedCount" to 0)), rego).allow)
        assertTrue(evaluator.evaluate(input(extraData = mapOf("approvedCount" to 1)), rego).allow)
    }

    // ── default allow = false + allow rules ─────────────────────────────────

    @Test
    fun `default allow false without matching allow rule denies`() {
        val rego = """
            package compliance
            default allow = false
            allow {
                input.approvalStatus == "APPROVED"
            }
        """.trimIndent()
        val result = evaluator.evaluate(input(approvalStatus = "PENDING"), rego)
        assertFalse(result.allow)
        assertEquals(listOf("No allow rule was satisfied"), result.denyReasons)
    }

    @Test
    fun `default allow false with matching allow rule permits`() {
        val rego = """
            package compliance
            default allow = false
            allow {
                input.approvalStatus == "APPROVED"
            }
        """.trimIndent()
        val result = evaluator.evaluate(input(approvalStatus = "APPROVED"), rego)
        assertTrue(result.allow)
    }

    @Test
    fun `default allow false multiple allow rules — any match permits`() {
        val rego = """
            package compliance
            default allow = false
            allow {
                input.environment == "staging"
            }
            allow {
                input.approvalStatus == "APPROVED"
            }
        """.trimIndent()
        assertFalse(evaluator.evaluate(input(environment = "production", approvalStatus = "PENDING"), rego).allow)
        assertTrue(evaluator.evaluate(input(environment = "staging", approvalStatus = "PENDING"), rego).allow)
        assertTrue(evaluator.evaluate(input(environment = "production", approvalStatus = "APPROVED"), rego).allow)
    }

    // ── count() conditions ───────────────────────────────────────────────────

    @Test
    fun `deny when attestation list is empty via count`() {
        val rego = """
            package compliance
            deny[msg] {
                count(input.attestations) == 0
                msg := "At least one attestation is required"
            }
        """.trimIndent()
        val withNone = PolicyInput(artifactName = "svc", attestations = emptyList())
        assertFalse(evaluator.evaluate(withNone, rego).allow)

        val withOne = PolicyInput(artifactName = "svc", attestations = listOf(mapOf("type" to "junit")))
        assertTrue(evaluator.evaluate(withOne, rego).allow)
    }

    // ── not operator ─────────────────────────────────────────────────────────

    @Test
    fun `not operator negates existence check`() {
        val rego = """
            package compliance
            deny[msg] {
                not input.environment
                msg := "Environment must be specified"
            }
        """.trimIndent()
        assertFalse(evaluator.evaluate(input(environment = null), rego).allow)
        assertTrue(evaluator.evaluate(input(environment = "staging"), rego).allow)
    }

    // ── old patterns still work (backward compatibility) ────────────────────

    @Test
    fun `old approvalStatus pattern still works`() {
        val rego = """
            package compliance
            default allow = false
            allow { input.approvalStatus == "APPROVED" }
        """.trimIndent()
        assertFalse(evaluator.evaluate(input(approvalStatus = "PENDING"), rego).allow)
        assertTrue(evaluator.evaluate(input(approvalStatus = "APPROVED"), rego).allow)
    }

    @Test
    fun `old criticalVulnerabilities pattern still works via deny rule`() {
        val rego = """
            package compliance
            deny[msg] {
                input.criticalVulnerabilities > 0
                msg := "criticalVulnerabilities is not 0"
            }
        """.trimIndent()
        assertTrue(evaluator.evaluate(input(extraData = mapOf("criticalVulnerabilities" to 0)), rego).allow)
        assertFalse(evaluator.evaluate(input(extraData = mapOf("criticalVulnerabilities" to 1)), rego).allow)
    }
}
