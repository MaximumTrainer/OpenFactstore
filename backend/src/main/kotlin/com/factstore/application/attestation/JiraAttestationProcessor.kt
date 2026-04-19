package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class JiraAttestationProcessor(private val objectMapper: ObjectMapper) : AttestationTypeProcessor {
    override val typeName = "jira"

    override fun process(evidenceContent: ByteArray, attestation: Attestation): Attestation {
        return runCatching {
            val content = evidenceContent.toString(Charsets.UTF_8).trim()

            if (content.startsWith("{")) {
                val root = objectMapper.readTree(evidenceContent)
                val issueRef = root.path("issueRef").asText()
                val status = root.path("status").asText()
                val isValidRef = issueRef.matches(Regex("[A-Z]+-\\d+"))
                attestation.status = if (isValidRef) AttestationStatus.PASSED else AttestationStatus.FAILED
                attestation.details = """{"issueRef":"$issueRef","status":"$status"}"""
            } else {
                val isValidRef = content.matches(Regex("[A-Z]+-\\d+"))
                attestation.status = if (isValidRef) AttestationStatus.PASSED else AttestationStatus.FAILED
                attestation.details = """{"issueRef":"$content"}"""
            }
            attestation
        }.getOrElse {
            attestation.status = AttestationStatus.FAILED
            attestation.details = """{"error":"Failed to parse Jira evidence: ${it.message?.replace("\"", "'")}"}"""
            attestation
        }
    }
}
