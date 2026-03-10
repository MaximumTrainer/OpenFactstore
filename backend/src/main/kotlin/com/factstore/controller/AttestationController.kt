package com.factstore.controller

import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.EvidenceFileResponse
import com.factstore.service.AttestationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/trails/{trailId}/attestations")
@Tag(name = "Attestations", description = "Attestation management")
class AttestationController(private val attestationService: AttestationService) {

    @PostMapping
    @Operation(summary = "Record an attestation for a trail")
    fun recordAttestation(
        @PathVariable trailId: UUID,
        @RequestBody request: CreateAttestationRequest
    ): ResponseEntity<AttestationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(attestationService.recordAttestation(trailId, request))

    @GetMapping
    @Operation(summary = "List attestations for a trail")
    fun listAttestations(@PathVariable trailId: UUID): ResponseEntity<List<AttestationResponse>> =
        ResponseEntity.ok(attestationService.listAttestations(trailId))

    @PostMapping("/{attestationId}/evidence", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload evidence file for an attestation")
    fun uploadEvidence(
        @PathVariable trailId: UUID,
        @PathVariable attestationId: UUID,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<EvidenceFileResponse> {
        val fileName = file.originalFilename ?: file.name
        val contentType = file.contentType ?: "application/octet-stream"
        return ResponseEntity.status(HttpStatus.CREATED).body(
            attestationService.uploadEvidence(trailId, attestationId, fileName, contentType, file.bytes)
        )
    }
}
