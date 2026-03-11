package com.factstore.application

import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.GenericWebhookPayload
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class GenericWebhookParser(private val objectMapper: ObjectMapper) {

    fun parse(payload: String): WebhookEvent {
        val body = objectMapper.readValue<GenericWebhookPayload>(payload)
        val (attestationType, attestationStatus) = mapEventToAttestation(body.eventType, body.attestationType, body.attestationStatus)
        return WebhookEvent(
            eventType = body.eventType,
            commitSha = body.gitCommitSha,
            branch = body.gitBranch,
            actor = body.gitAuthor,
            actorEmail = body.gitAuthorEmail,
            attestationType = attestationType,
            attestationStatus = attestationStatus,
            details = body.details,
            trailId = body.trailId
        )
    }

    private fun mapEventToAttestation(
        eventType: String,
        explicitType: String?,
        explicitStatus: AttestationStatus?
    ): Pair<String?, AttestationStatus?> {
        if (explicitType != null && explicitStatus != null) {
            return Pair(explicitType, explicitStatus)
        }
        return when (eventType) {
            "build.succeeded" -> Pair("BUILD", AttestationStatus.PASSED)
            "build.failed" -> Pair("BUILD", AttestationStatus.FAILED)
            "test.passed" -> Pair("TEST", AttestationStatus.PASSED)
            "test.failed" -> Pair("TEST", AttestationStatus.FAILED)
            "scan.passed" -> Pair("SECURITY_SCAN", AttestationStatus.PASSED)
            "scan.failed" -> Pair("SECURITY_SCAN", AttestationStatus.FAILED)
            else -> Pair(explicitType, explicitStatus)
        }
    }
}
