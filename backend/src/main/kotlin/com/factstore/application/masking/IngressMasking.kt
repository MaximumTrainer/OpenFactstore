package com.factstore.application.masking

import com.factstore.application.observability.FailureMetrics
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.application.schema.SchemaViolation
import com.factstore.core.domain.SchemaUrn
import com.factstore.exception.FactRejectedException
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** What masking decided about a payload: what to store, what was masked, under which ruleset. */
data class MaskingOutcome(val payload: JsonNode, val maskedPaths: List<String>, val ruleset: String)

/**
 * Masking at the ingestion boundary (#198), in one of two modes (`factstore.masking.mode`):
 *
 * - `reject` (default): a payload with an unmasked secret, PII, or an unmasked `x-sensitive` field
 *   is refused with `400 unmasked_secret_detected`. Runners mask before they sign, so the stored
 *   bytes are exactly the signed bytes and the source signature (#187) stays verifiable.
 * - `redact`: Factstore masks the payload itself and stores the masked version. The signed bytes
 *   are then not stored, only their hash.
 *
 * The ruleset is the built-in `builtin-v1`, or `factstore.masking.rules-file`.
 */
@Component
class IngressMasking(
    private val schemas: SchemaRegistryService,
    private val failureMetrics: FailureMetrics,
    @Value("\${factstore.masking.mode:reject}") mode: String,
    @Value("\${factstore.masking.rules-file:}") rulesFile: String
) {
    private val redact = when (mode.lowercase()) {
        "reject" -> false
        "redact" -> true
        else -> error("factstore.masking.mode must be reject or redact, got '$mode'")
    }

    private val masking = DataMasking(if (rulesFile.isBlank()) MaskingRuleset.builtIn() else MaskingRuleset.fromFile(rulesFile))

    fun check(schemaRef: SchemaUrn, tool: JsonNode): MaskingOutcome {
        val result = masking.scan(tool, schemas.sensitivePointers(schemaRef))
        if (result.findings.isEmpty()) return MaskingOutcome(tool, emptyList(), masking.version)
        if (!redact) {
            failureMetrics.ingestDenial("UNMASKED_SECRET")
            throw FactRejectedException(
                FactRejectedException.UNMASKED_SECRET,
                schemaRef.value,
                result.findings.map { SchemaViolation("/tool${it.path}", "unmasked value matched masking rule ${it.rule}") },
                maskingRuleset = masking.version
            )
        }
        return MaskingOutcome(result.masked, result.findings.map { "/tool${it.path}" }.distinct(), masking.version)
    }
}
