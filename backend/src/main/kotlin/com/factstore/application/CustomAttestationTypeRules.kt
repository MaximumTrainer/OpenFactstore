package com.factstore.application

import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import com.factstore.exception.BadRequestException
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

/**
 * What a custom attestation type asks of an attestation's data: it must match the type's JSON
 * schema. Shared by the v1 service and the v2 command handler so both paths validate the same
 * data the same way (#250). The type's jq-like expression is evaluated by
 * `AttestationVerdictResolver` (#240).
 */
class CustomAttestationTypeRules(
    private val customAttestationTypeRepository: ICustomAttestationTypeRepository,
    private val objectMapper: ObjectMapper
) {
    /** Throws [BadRequestException] when [data] does not match the schema of the custom [type]. */
    fun validate(type: String, data: String?) {
        if (data == null) return
        val schemaJson = customAttestationTypeRepository.findByName(type)?.schemaJson ?: return
        validateDataAgainstSchema(data, schemaJson, type)
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
}
