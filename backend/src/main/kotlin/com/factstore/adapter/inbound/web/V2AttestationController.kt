package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IAttestationService
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v2/attestations")
@Tag(name = "V2 Attestations", description = "V2 attestation API")
class V2AttestationController(private val attestationService: IAttestationService) {

    @PostMapping("/{org}/{flow}/{trail}/{artifactFingerprint}")
    @Operation(summary = "Record an attestation (v2 format)")
    fun recordAttestation(
        @PathVariable org: String,
        @PathVariable flow: String,
        @PathVariable trail: UUID,
        @PathVariable artifactFingerprint: String,
        @RequestBody request: CreateAttestationRequest
    ): ResponseEntity<AttestationResponse> {
        val response = attestationService.recordAttestation(
            trailId = trail,
            request = request,
            artifactFingerprint = artifactFingerprint,
            orgSlug = org,
            flowName = flow
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
