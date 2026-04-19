package com.factstore.adapter.inbound.web.command

import com.factstore.application.DryRunContext
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.command.IAttestationCommandHandler
import com.factstore.core.port.inbound.IPullRequestAttestationService
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.RecordAttestationCommand
import com.factstore.dto.command.RecordAttestationRequest
import com.factstore.dto.command.UploadEvidenceCommand
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreatePrAttestationRequest
import com.factstore.dto.DryRunResponse
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
@RequestMapping("/api/v2/trails/{trailId}/attestations")
@Tag(name = "Attestations – Commands", description = "Attestation write operations (CQRS command path)")
class AttestationCommandController(
    private val commandHandler: IAttestationCommandHandler,
    private val pullRequestAttestationService: IPullRequestAttestationService
) {

    @PostMapping
    @Operation(summary = "Record an attestation for a trail")
    fun recordAttestation(
        @PathVariable trailId: UUID,
        @RequestBody request: RecordAttestationRequest,
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
        val command = RecordAttestationCommand(
            trailId = trailId,
            type = request.type,
            status = request.status,
            details = request.details,
            name = request.name,
            evidenceUrl = request.evidenceUrl,
            orgSlug = request.orgSlug,
            artifactFingerprint = request.artifactFingerprint,
            flowName = request.flowName
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(commandHandler.recordAttestation(command))
    }

    @PostMapping("/{attestationId}/evidence", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload evidence file for an attestation")
    fun uploadEvidence(
        @PathVariable trailId: UUID,
        @PathVariable attestationId: UUID,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<CommandResult> {
        val fileName = file.originalFilename ?: file.name
        val contentType = file.contentType ?: "application/octet-stream"
        return ResponseEntity.status(HttpStatus.CREATED).body(
            commandHandler.uploadEvidence(
                UploadEvidenceCommand(
                    trailId = trailId,
                    attestationId = attestationId,
                    fileName = fileName,
                    contentType = contentType,
                    content = file.bytes
                )
            )
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
