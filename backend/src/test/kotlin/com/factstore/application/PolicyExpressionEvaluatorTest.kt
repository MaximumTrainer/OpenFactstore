package com.factstore.application

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
    fun `malformed expression returns false not exception`() {
        val context = ctx(flowName = "my-flow")
        assertFalse(evaluator.evaluate("this is not a valid expression !!!", context))
        assertFalse(evaluator.evaluate("== broken ==", context))
    }

    @Test
    fun `expression with dollar-brace wrapper is handled`() {
        val context = ctx(flowName = "test")
        assertTrue(evaluator.evaluate("\${{ flow.name == \"test\" }}", context))
    }
}
