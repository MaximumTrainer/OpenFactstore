package com.factstore.adapter.inbound.web

import com.factstore.application.OidcAttestationRequest
import com.factstore.application.OidcAttestationService
import com.factstore.dto.AttestationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class OidcAttestationApiRequest(
    val trailId: UUID,
    val token: String,
    val orgSlug: String? = null
)

@RestController
@RequestMapping("/api/v2/attestations/oidc")
@Tag(name = "OIDC Attestations", description = "OIDC token-based provenance attestation")
class OidcAttestationController(private val oidcAttestationService: OidcAttestationService) {

    @PostMapping
    @Operation(summary = "Record an OIDC token attestation (GitHub Actions / GitLab CI)")
    fun recordOidcAttestation(@RequestBody request: OidcAttestationApiRequest): ResponseEntity<AttestationResponse> {
        val response = oidcAttestationService.recordOidcAttestation(
            OidcAttestationRequest(
                trailId = request.trailId,
                token = request.token,
                orgSlug = request.orgSlug
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
