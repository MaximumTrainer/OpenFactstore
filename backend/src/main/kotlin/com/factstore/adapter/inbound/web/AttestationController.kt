package com.factstore.adapter.inbound.web

import com.factstore.application.DryRunContext
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IPullRequestAttestationService
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreatePrAttestationRequest
import com.factstore.dto.DryRunResponse
import com.factstore.dto.EvidenceFileResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/trails/{trailId}/attestations")
@Tag(name = "Attestations", description = "Attestation management")
class AttestationController(
    private val attestationService: IAttestationService,
    private val pullRequestAttestationService: IPullRequestAttestationService
) {

    @PostMapping
    @Operation(
        summary = "Record an attestation for a trail",
        deprecated = true,
        description = "⚠️ Deprecated: use the Kosli v2 endpoint " +
            "POST /api/v2/attestations/{org}/{flow}/{trail}/{artifactFingerprint} instead."
    )
    fun recordAttestation(
        @PathVariable trailId: UUID,
        @RequestBody request: CreateAttestationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val wouldBe = AttestationResponse(
                id = UUID.randomUUID(),
                trailId = trailId,
                type = request.type,
                status = request.status,
                evidenceFileHash = null,
                evidenceFileName = null,
                evidenceFileSizeBytes = null,
                details = request.details,
                name = request.name,
                evidenceUrl = request.evidenceUrl,
                compliant = request.status == AttestationStatus.PASSED,
                orgSlug = request.orgSlug,
                createdAt = Instant.now()
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(attestationService.recordAttestation(trailId, request))
    }

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

    @PostMapping("/pull-request")
    @Operation(summary = "Record a pull request attestation by querying the SCM provider")
    fun attestPullRequest(
        @PathVariable trailId: UUID,
        @RequestBody request: CreatePrAttestationRequest
    ): ResponseEntity<AttestationResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(pullRequestAttestationService.attestPullRequest(trailId, request))
}
