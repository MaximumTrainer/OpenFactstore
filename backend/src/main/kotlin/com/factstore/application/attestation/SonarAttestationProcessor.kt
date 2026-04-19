package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class SonarAttestationProcessor(private val objectMapper: ObjectMapper) : AttestationTypeProcessor {
    override val typeName = "sonar"

    override fun process(evidenceContent: ByteArray, attestation: Attestation): Attestation {
        return runCatching {
            val root = objectMapper.readTree(evidenceContent)
            // Accept SonarQube quality gate API response format:
            // {"projectStatus": {"status": "OK"}} or {"projectStatus": {"status": "ERROR"}}
            val status = root.path("projectStatus").path("status").asText()
                .ifEmpty { root.path("status").asText() }

            attestation.status = if (status.equals("OK", ignoreCase = true) ||
                status.equals("PASSED", ignoreCase = true))
                AttestationStatus.PASSED else AttestationStatus.FAILED
            attestation.details = """{"qualityGateStatus":"$status"}"""
            attestation
        }.getOrElse {
            attestation.status = AttestationStatus.FAILED
            attestation.details = """{"error":"Failed to parse SonarQube response: ${it.message?.replace("\"", "'")}"}"""
            attestation
        }
    }
}
