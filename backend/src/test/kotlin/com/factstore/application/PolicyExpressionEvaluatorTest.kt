package com.factstore.application

import com.factstore.application.policy.ConditionException
import com.factstore.application.policy.PolicyExpressionEvaluator
import com.factstore.application.policy.PolicyExpressionEvaluator.EvaluationContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PolicyExpressionEvaluatorTest {

    private val evaluator = PolicyExpressionEvaluator()

    private fun ctx(
        flowName: String? = null,
        flowTags: Map<String, String> = emptyMap(),
        artifactName: String? = null,
        artifactFingerprint: String? = null
    ) = EvaluationContext(flowName, flowTags, artifactName, artifactFingerprint)

    @Test
    fun `flow name equals expression matches`() {
        val context = ctx(flowName = "backend-service")
        assertTrue(evaluator.evaluate("""flow.name == "backend-service"""", context))
    }

    @Test
    fun `flow name equals expression does not match different name`() {
        val context = ctx(flowName = "frontend-service")
        assertFalse(evaluator.evaluate("""flow.name == "backend-service"""", context))
    }

    @Test
    fun `flow name not-equals expression matches`() {
        val context = ctx(flowName = "frontend-service")
        assertTrue(evaluator.evaluate("""flow.name != "backend-service"""", context))
    }

    @Test
    fun `artifact name equals expression matches`() {
        val context = ctx(artifactName = "my-image")
        assertTrue(evaluator.evaluate("""artifact.name == "my-image"""", context))
    }

    @Test
    fun `exists(flow) returns true when flow name set`() {
        val context = ctx(flowName = "some-flow")
        assertTrue(evaluator.evaluate("exists(flow)", context))
    }

    @Test
    fun `exists(flow) returns false when flow name is null`() {
        val context = ctx(flowName = null)
        assertFalse(evaluator.evaluate("exists(flow)", context))
    }

    @Test
    fun `matches regex pattern on flow name`() {
        val context = ctx(flowName = "backend-service-v2")
        assertTrue(evaluator.evaluate("""matches(flow.name, "^backend.*")""", context))
    }

    @Test
    fun `matches regex pattern on artifact name`() {
        val context = ctx(artifactName = "my-app-1.0.0")
        assertTrue(evaluator.evaluate("""matches(artifact.name, "^my-app-.*")""", context))
        assertFalse(evaluator.evaluate("""matches(artifact.name, "^other-.*")""", context))
    }

    @Test
    fun `and expression both true`() {
        val context = ctx(flowName = "secure-flow")
        assertTrue(evaluator.evaluate("""flow.name == "secure-flow" and exists(flow)""", context))
    }

    @Test
    fun `and expression one false returns false`() {
        val context = ctx(flowName = "other-flow")
        assertFalse(evaluator.evaluate("""flow.name == "secure-flow" and exists(flow)""", context))
    }

    @Test
    fun `or expression one true returns true`() {
        val context = ctx(flowName = "other-flow")
        assertTrue(evaluator.evaluate("""flow.name == "secure-flow" or exists(flow)""", context))
    }

    @Test
    fun `or expression both false returns false`() {
        val context = ctx(flowName = null)
        assertFalse(evaluator.evaluate("""flow.name == "secure-flow" or exists(flow)""", context))
    }

    @Test
    fun `not expression inverts result`() {
        assertTrue(evaluator.evaluate("not exists(flow)", ctx(flowName = null)))
        assertFalse(evaluator.evaluate("not exists(flow)", ctx(flowName = "some-flow")))
    }

    @Test
    fun `field in list matches`() {
        val context = ctx(flowName = "backend-service")
        assertTrue(evaluator.evaluate("""flow.name in ["backend-service", "frontend-service"]""", context))
    }

    @Test
    fun `field not in list returns false`() {
        val context = ctx(flowName = "unknown-service")
        assertFalse(evaluator.evaluate("""flow.name in ["backend-service", "frontend-service"]""", context))
    }

    @Test
    fun `malformed expression throws instead of silently evaluating to false`() {
        val context = ctx(flowName = "my-flow")
        assertThrows(ConditionException::class.java) {
            evaluator.evaluate("this is not a valid expression !!!", context)
        }
        assertThrows(ConditionException::class.java) { evaluator.evaluate("== broken ==", context) }
    }

    @Test
    fun `unknown field is an error naming the field`() {
        val e = assertThrows(ConditionException::class.java) {
            evaluator.evaluate("""flow.nmae == "payments"""", ctx(flowName = "payments"))
        }
        assertTrue(e.message!!.contains("flow.nmae"), e.message)
        assertEquals(1, e.position)
    }

    @Test
    fun `unknown function is an error`() {
        val e = assertThrows(ConditionException::class.java) {
            evaluator.validate("""startsWith(flow.name, "pay")""")
        }
        assertTrue(e.message!!.contains("startsWith"), e.message)
    }

    @Test
    fun `stray quote is a syntax error with its position`() {
        val e = assertThrows(ConditionException::class.java) {
            evaluator.validate("""flow.name == "payments""")
        }
        assertEquals(14, e.position)
    }

    @Test
    fun `trailing tokens are a syntax error`() {
        assertThrows(ConditionException::class.java) {
            evaluator.validate("""flow.name == "a" "b"""")
        }
    }

    @Test
    fun `invalid regex in matches is an error`() {
        assertThrows(ConditionException::class.java) {
            evaluator.validate("""matches(flow.name, "([")""")
        }
    }

    @Test
    fun `and binds tighter than or`() {
        val expr = """flow.name == "x" and artifact.name == "y" or artifact.fingerprint == "z""""
        // (false and true) or true
        assertTrue(evaluator.evaluate(expr, ctx(flowName = "other", artifactName = "y", artifactFingerprint = "z")))
        // (true and false) or false
        assertFalse(evaluator.evaluate(expr, ctx(flowName = "x", artifactName = "other", artifactFingerprint = "other")))
        // (true and true) or false
        assertTrue(evaluator.evaluate(expr, ctx(flowName = "x", artifactName = "y", artifactFingerprint = "other")))
    }

    @Test
    fun `or followed by and keeps and precedence`() {
        val expr = """flow.name == "x" or artifact.name == "y" and artifact.fingerprint == "z""""
        // true or (false and false) -> true; naive left-to-right would give false
        assertTrue(evaluator.evaluate(expr, ctx(flowName = "x", artifactName = "no", artifactFingerprint = "no")))
    }

    @Test
    fun `parentheses override precedence`() {
        val expr = """(flow.name == "x" or artifact.name == "y") and artifact.fingerprint == "z""""
        assertFalse(evaluator.evaluate(expr, ctx(flowName = "x", artifactName = "y", artifactFingerprint = "no")))
        assertTrue(evaluator.evaluate(expr, ctx(flowName = "x", artifactFingerprint = "z")))
    }

    @Test
    fun `quoted value containing and or or is compared verbatim`() {
        val context = ctx(flowName = "salt and pepper or vinegar")
        assertTrue(evaluator.evaluate("""flow.name == "salt and pepper or vinegar"""", context))
        assertFalse(evaluator.evaluate("""flow.name == "salt and pepper"""", context))
        assertTrue(evaluator.evaluate("""flow.name in ["a, b", "salt and pepper or vinegar"]""", context))
    }

    @Test
    fun `not applies to a single operand`() {
        val expr = """not flow.name == "x" and exists(flow)"""
        assertTrue(evaluator.evaluate(expr, ctx(flowName = "y")))
        assertFalse(evaluator.evaluate(expr, ctx(flowName = "x")))
    }

    @Test
    fun `flow tags can be read with dot or bracket syntax`() {
        val context = ctx(flowName = "f", flowTags = mapOf("pci-scope" to "true", "team" to "core"))
        assertTrue(evaluator.evaluate("""flow.tags["pci-scope"] == "true"""", context))
        assertTrue(evaluator.evaluate("""flow.tags.team == "core"""", context))
        assertFalse(evaluator.evaluate("""flow.tags["missing"] == "true"""", context))
    }

    @Test
    fun `single-quoted string literals are accepted`() {
        assertTrue(evaluator.evaluate("flow.name == 'payments'", ctx(flowName = "payments")))
    }

    @Test
    fun `exists accepts only known subjects`() {
        assertTrue(evaluator.evaluate("exists(artifact)", ctx(artifactName = "a")))
        assertThrows(ConditionException::class.java) { evaluator.validate("exists(flwo)") }
    }

    @Test
    fun `blank expression is an error`() {
        assertThrows(ConditionException::class.java) { evaluator.validate("   ") }
        assertThrows(ConditionException::class.java) { evaluator.validate("\${{ }}") }
    }

    @Test
    fun `expression with dollar-brace wrapper is handled`() {
        val context = ctx(flowName = "test")
        assertTrue(evaluator.evaluate("\${{ flow.name == \"test\" }}", context))
    }
}
