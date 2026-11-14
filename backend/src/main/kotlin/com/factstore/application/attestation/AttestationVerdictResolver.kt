package com.factstore.application.attestation

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Decides the status an attestation is recorded with (#240). The caller's claimed status is
 * only trusted where the server has no way to check it:
 *
 * - **Types with an evidence processor** (junit, snyk, sonar, jira): a claimed `PASSED` is
 *   ignored and the attestation is `PENDING` until evidence is uploaded and the processor decides.
 *   A claimed `FAILED` is kept, since failing closed is always safe.
 * - **Custom types with a jq expression**: the expression's result on `attestationData` decides;
 *   with no data, or data the expression cannot read, the attestation is `PENDING`.
 * - **Anything else**: the claim is recorded as given and marked self-asserted, so assert and
 *   gate output can show it and a flow can refuse to count it.
 */
@Component
class AttestationVerdictResolver(
    private val processors: List<AttestationTypeProcessor>,
    private val customTypes: ICustomAttestationTypeRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AttestationVerdictResolver::class.java)

    data class Verdict(val status: AttestationStatus, val selfAsserted: Boolean)

    private fun processorFor(type: String) = processors.firstOrNull { it.typeName.equals(type, ignoreCase = true) }

    fun hasProcessor(type: String): Boolean = processorFor(type) != null

    /**
     * Lets the type's processor, if any, decide the attestation's status from uploaded evidence.
     * Returns true when a processor ran; the status is then the server's, never self-asserted.
     */
    fun applyEvidence(attestation: com.factstore.core.domain.Attestation, evidenceContent: ByteArray): Boolean {
        val processor = processorFor(attestation.type) ?: return false
        processor.process(evidenceContent, attestation)
        attestation.selfAsserted = false
        return true
    }

    fun resolve(type: String, claimed: AttestationStatus, attestationData: String?): Verdict {
        if (hasProcessor(type)) {
            // Its status is derived from uploaded evidence; only a claimed failure is kept.
            val status = if (claimed == AttestationStatus.FAILED) AttestationStatus.FAILED else AttestationStatus.PENDING
            return Verdict(status, selfAsserted = false)
        }
        val jq = customTypes.findByName(type)?.jqExpression
        if (jq != null) {
            val result = attestationData?.let { data ->
                runCatching { evaluateJqExpression(jq, objectMapper.readTree(data)) }
                    .onFailure { log.warn("Could not evaluate jq '{}' for type '{}': {}", jq, type, it.message) }
                    .getOrNull()
            }
            val status = when (result) {
                true -> AttestationStatus.PASSED
                false -> AttestationStatus.FAILED
                null -> AttestationStatus.PENDING
            }
            return Verdict(status, selfAsserted = false)
        }
        return Verdict(claimed, selfAsserted = claimed != AttestationStatus.PENDING)
    }

    /**
     * Evaluates a simple jq-like expression against a JSON node.
     * Supports: `.field`, `.field == value`, `.field.nested == value`.
     * Returns null when the expression cannot be evaluated against [node].
     */
    private fun evaluateJqExpression(expression: String, node: JsonNode): Boolean? {
        val trimmed = expression.trim()
        return if (trimmed.contains("==")) {
            val parts = trimmed.split("==", limit = 2)
            val fieldNode = resolveFieldPath(parts[0].trim(), node) ?: return null
            val expectedRaw = parts[1].trim()
            when (expectedRaw) {
                "true" -> fieldNode.asBoolean()
                "false" -> !fieldNode.asBoolean()
                else -> fieldNode.asText() == expectedRaw.removeSurrounding("\"")
            }
        } else {
            val fieldNode = resolveFieldPath(trimmed, node) ?: return null
            when {
                fieldNode.isBoolean -> fieldNode.asBoolean()
                fieldNode.isNull || fieldNode.isMissingNode -> false
                else -> true
            }
        }
    }

    private fun resolveFieldPath(path: String, node: JsonNode): JsonNode? {
        var current: JsonNode = node
        for (part in path.trimStart('.').split(".")) {
            if (part.isBlank()) continue
            current = current.get(part) ?: return null
        }
        return current
    }
}
