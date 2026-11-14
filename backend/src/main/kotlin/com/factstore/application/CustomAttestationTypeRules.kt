package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import com.factstore.exception.BadRequestException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.slf4j.LoggerFactory

/**
 * What a custom attestation type asks of an attestation's data: it must match the type's JSON
 * schema, and the type's jq-like expression decides PASSED or FAILED. Shared by the v1 service and
 * the v2 command handler so both paths judge the same data the same way (#250).
 */
class CustomAttestationTypeRules(
    private val customAttestationTypeRepository: ICustomAttestationTypeRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(CustomAttestationTypeRules::class.java)

    /** Throws [BadRequestException] when [data] does not match the schema of the custom [type]. */
    fun validate(type: String, data: String?) {
        if (data == null) return
        val schemaJson = customAttestationTypeRepository.findByName(type)?.schemaJson ?: return
        validateDataAgainstSchema(data, schemaJson, type)
    }

    /** Sets the status from the custom type's expression, when it has one and it can be evaluated. */
    fun evaluate(attestation: Attestation) {
        val data = attestation.attestationData ?: return
        val expression = customAttestationTypeRepository.findByName(attestation.type)?.jqExpression ?: return
        applyJqExpression(attestation, expression, data)
    }

    private fun validateDataAgainstSchema(data: String, schemaJson: String, typeName: String) {
        try {
            val dataNode = objectMapper.readTree(data)
            val schemaNode = objectMapper.readTree(schemaJson)
            val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            val schema = factory.getSchema(schemaNode)
            val errors = schema.validate(dataNode)
            if (errors.isNotEmpty()) {
                val message = errors.take(3).joinToString("; ") { it.message }
                throw BadRequestException("attestationData does not conform to schema for type '$typeName': $message")
            }
        } catch (e: BadRequestException) {
            throw e
        } catch (e: Exception) {
            throw BadRequestException("attestationData is not valid JSON for type '$typeName': ${e.message}")
        }
    }

    private fun applyJqExpression(attestation: Attestation, jqExpression: String, attestationData: String) {
        try {
            val node = objectMapper.readTree(attestationData)
            val result = evaluateJqExpression(jqExpression, node)
            if (result != null) {
                attestation.status = if (result) AttestationStatus.PASSED else AttestationStatus.FAILED
            }
        } catch (e: Exception) {
            log.warn("Failed to evaluate jq expression '{}' for attestation type '{}': {}", jqExpression, attestation.type, e.message)
        }
    }

    /**
     * Evaluates a simple jq-like expression against a JSON node.
     * Supports: `.field`, `.field == value`, `.field.nested == value`
     * Returns null if the expression is not supported or cannot be evaluated.
     */
    private fun evaluateJqExpression(expression: String, node: JsonNode): Boolean? {
        val trimmed = expression.trim()
        return if (trimmed.contains("==")) {
            val parts = trimmed.split("==", limit = 2)
            val fieldPath = parts[0].trim()
            val expectedRaw = parts[1].trim()
            val fieldNode = resolveFieldPath(fieldPath, node) ?: return null
            val expected = expectedRaw.removeSurrounding("\"")
            when {
                expectedRaw == "true" -> fieldNode.asBoolean() == true
                expectedRaw == "false" -> fieldNode.asBoolean() == false
                expectedRaw.startsWith("\"") -> fieldNode.asText() == expected
                else -> fieldNode.asText() == expected
            }
        } else {
            val fieldNode = resolveFieldPath(trimmed, node) ?: return null
            when {
                fieldNode.isBoolean -> fieldNode.asBoolean()
                fieldNode.isNull -> false
                fieldNode.isMissingNode -> false
                else -> true
            }
        }
    }

    private fun resolveFieldPath(path: String, node: JsonNode): JsonNode? {
        val parts = path.trimStart('.').split(".")
        var current: JsonNode = node
        for (part in parts) {
            if (part.isBlank()) continue
            current = current.get(part) ?: return null
        }
        return current
    }
}
