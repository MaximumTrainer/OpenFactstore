package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.OidcJtiLog
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IOidcJtiLogRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AttestationResponse
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import java.util.UUID

data class OidcAttestationRequest(
    val trailId: UUID,
    val token: String,
    val orgSlug: String? = null
)

@Service
@Transactional
class OidcAttestationService(
    private val trailRepository: ITrailRepository,
    private val attestationRepository: IAttestationRepository,
    private val jtiLogRepository: IOidcJtiLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(OidcAttestationService::class.java)

    private val allowedIssuers = setOf(
        "https://token.actions.githubusercontent.com",
        "https://gitlab.com"
    )

    fun recordOidcAttestation(request: OidcAttestationRequest): AttestationResponse {
        if (!trailRepository.existsById(request.trailId)) {
            throw NotFoundException("Trail not found: ${request.trailId}")
        }

        val claims = parseAndVerifyToken(request.token)
        val issuer = claims["iss"] as? String ?: throw IllegalArgumentException("Missing 'iss' claim")
        val jti = claims["jti"] as? String ?: UUID.randomUUID().toString()

        if (!allowedIssuers.contains(issuer)) {
            throw IllegalArgumentException("Untrusted OIDC issuer: $issuer")
        }

        if (jtiLogRepository.existsByJti(jti)) {
            throw ConflictException("OIDC token already used (jti: $jti)")
        }

        jtiLogRepository.save(OidcJtiLog(jti = jti, issuer = issuer))

        val details = objectMapper.writeValueAsString(extractClaims(claims))
        val attestation = Attestation(
            trailId = request.trailId,
            type = "oidc-provenance",
            status = AttestationStatus.PASSED,
            orgSlug = request.orgSlug,
            details = details
        )
        val saved = attestationRepository.save(attestation)
        log.info("Recorded OIDC attestation for trail={} issuer={}", request.trailId, issuer)
        return saved.toResponse()
    }

    private fun parseAndVerifyToken(token: String): Map<String, Any> {
        // Decode JWT payload (no signature verification in this implementation —
        // in production, verify against JWKS endpoint)
        val parts = token.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid JWT format")
        val payloadJson = String(Base64.getUrlDecoder().decode(padBase64(parts[1])))
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any>
    }

    private fun extractClaims(claims: Map<String, Any>): Map<String, Any?> = mapOf(
        "issuer" to claims["iss"],
        "subject" to claims["sub"],
        "repository" to (claims["repository"] ?: claims["project_path"]),
        "workflow" to (claims["workflow"] ?: claims["ci_config_ref_uri"]),
        "ref" to claims["ref"],
        "sha" to (claims["sha"] ?: claims["pipeline_source"]),
        "actor" to (claims["actor"] ?: claims["user_login"]),
        "runId" to claims["run_id"],
        "issuedAt" to claims["iat"]
    )

    private fun padBase64(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }
}
