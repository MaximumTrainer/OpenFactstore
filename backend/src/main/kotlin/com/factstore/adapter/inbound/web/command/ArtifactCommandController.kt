package com.factstore.adapter.inbound.web.command

import com.factstore.application.DryRunContext
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.port.inbound.command.IArtifactCommandHandler
import com.factstore.core.port.inbound.IBuildProvenanceService
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.ReportArtifactCommand
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.BuildProvenanceResponse
import com.factstore.dto.DryRunResponse
import com.factstore.dto.ProvenanceVerificationResponse
import com.factstore.dto.RecordProvenanceRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@Tag(name = "Artifacts – Commands", description = "Artifact write operations (CQRS command path)")
class ArtifactCommandController(
    private val commandHandler: IArtifactCommandHandler,
    private val buildProvenanceService: IBuildProvenanceService
) {

    @PostMapping("/api/v2/trails/{trailId}/artifacts")
    @Operation(summary = "Report an artifact for a trail")
    fun reportArtifact(
        @PathVariable trailId: UUID,
        @RequestBody command: ReportArtifactCommand,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val wouldBe = ArtifactResponse(
                id = UUID.randomUUID(),
                trailId = trailId,
                imageName = command.imageName,
                imageTag = command.imageTag,
                sha256Digest = command.sha256Digest,
                registry = command.registry,
                reportedAt = Instant.now(),
                reportedBy = command.reportedBy,
                orgSlug = command.orgSlug,
                provenanceStatus = ProvenanceStatus.NO_PROVENANCE
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(commandHandler.reportArtifact(command.copy(trailId = trailId)))
    }

    @PostMapping("/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance")
    @Operation(summary = "Record build provenance for an artifact")
    fun recordProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID,
        @RequestBody request: RecordProvenanceRequest
    ): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(buildProvenanceService.recordProvenance(trailId, artifactId, request))

    @PostMapping("/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance/verify")
    @Operation(summary = "Verify the provenance signature of an artifact")
    fun verifyProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID
    ): ResponseEntity<ProvenanceVerificationResponse> =
        ResponseEntity.ok(buildProvenanceService.verifyProvenance(trailId, artifactId))
}
