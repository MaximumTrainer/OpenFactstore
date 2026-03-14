package com.factstore.adapter.inbound.web

import com.factstore.application.DryRunContext
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.port.inbound.IArtifactService
import com.factstore.core.port.inbound.IBuildProvenanceService
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.BuildProvenanceResponse
import com.factstore.dto.CreateArtifactRequest
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
@Tag(name = "Artifacts", description = "Artifact management")
class ArtifactController(
    private val artifactService: IArtifactService,
    private val buildProvenanceService: IBuildProvenanceService
) {

    @PostMapping("/api/v1/trails/{trailId}/artifacts")
    @Operation(summary = "Report an artifact for a trail")
    fun reportArtifact(
        @PathVariable trailId: UUID,
        @RequestBody request: CreateArtifactRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val wouldBe = ArtifactResponse(
                id = UUID.randomUUID(),
                trailId = trailId,
                imageName = request.imageName,
                imageTag = request.imageTag,
                sha256Digest = request.sha256Digest,
                registry = request.registry,
                reportedAt = Instant.now(),
                reportedBy = request.reportedBy,
                orgSlug = request.orgSlug,
                provenanceStatus = ProvenanceStatus.NO_PROVENANCE
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(artifactService.reportArtifact(trailId, request))
    }

    @GetMapping("/api/v1/trails/{trailId}/artifacts")
    @Operation(summary = "List artifacts for a trail")
    fun listArtifacts(@PathVariable trailId: UUID): ResponseEntity<List<ArtifactResponse>> =
        ResponseEntity.ok(artifactService.listArtifactsForTrail(trailId))

    @GetMapping("/api/v1/artifacts")
    @Operation(summary = "Find artifacts by SHA256 digest")
    fun findBySha256(@RequestParam sha256: String): ResponseEntity<List<ArtifactResponse>> =
        ResponseEntity.ok(artifactService.findBySha256(sha256))

    @PostMapping("/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance")
    @Operation(summary = "Record build provenance for an artifact")
    fun recordProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID,
        @RequestBody request: RecordProvenanceRequest
    ): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(buildProvenanceService.recordProvenance(trailId, artifactId, request))

    @GetMapping("/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance")
    @Operation(summary = "Retrieve build provenance for an artifact")
    fun getProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID
    ): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.ok(buildProvenanceService.getProvenance(trailId, artifactId))

    @GetMapping("/api/v1/artifacts/{sha256}/provenance")
    @Operation(summary = "Get provenance by artifact SHA256 digest")
    fun getProvenanceBySha256(@PathVariable sha256: String): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.ok(buildProvenanceService.getProvenanceBySha256(sha256))

    @PostMapping("/api/v1/trails/{trailId}/artifacts/{artifactId}/provenance/verify")
    @Operation(summary = "Verify the provenance signature of an artifact")
    fun verifyProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID
    ): ResponseEntity<ProvenanceVerificationResponse> =
        ResponseEntity.ok(buildProvenanceService.verifyProvenance(trailId, artifactId))
}
