package com.factstore.application

import com.factstore.application.auth.WorkloadTokenVerifier
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
    private val objectMapper: ObjectMapper,
    private val tokenVerifier: WorkloadTokenVerifier
) {
    private val log = LoggerFactory.getLogger(OidcAttestationService::class.java)


    fun recordOidcAttestation(request: OidcAttestationRequest): AttestationResponse {
        if (!trailRepository.existsById(request.trailId)) {
            throw NotFoundException("Trail not found: ${request.trailId}")
        }

        // Signature, trusted issuer, audience and expiry are all checked before a claim is
        // believed (#189). This used to base64-decode the token and trust it.
        val claims = tokenVerifier.verify(request.token)
        val issuer = claims["iss"] as String
        val jti = claims["jti"] as? String ?: UUID.randomUUID().toString()

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

}
