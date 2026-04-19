package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class SnykAttestationProcessor(private val objectMapper: ObjectMapper) : AttestationTypeProcessor {
    override val typeName = "snyk"

    override fun process(evidenceContent: ByteArray, attestation: Attestation): Attestation {
        return runCatching {
            val root = objectMapper.readTree(evidenceContent)

            val results = root.path("runs").let { runs ->
                if (!runs.isMissingNode && runs.isArray) {
                    // SARIF format
                    var total = 0
                    runs.forEach { run -> total += run.path("results").size() }
                    total
                } else {
                    // Snyk JSON format: vulnerabilities array
                    root.path("vulnerabilities").size()
                }
            }

            attestation.status = if (results > 0) AttestationStatus.FAILED else AttestationStatus.PASSED
            attestation.details = """{"vulnerabilities":$results}"""
            attestation
        }.getOrElse {
            attestation.status = AttestationStatus.FAILED
            attestation.details = """{"error":"Failed to parse Snyk/SARIF output: ${it.message?.replace("\"", "'")}"}"""
            attestation
        }
    }
}
