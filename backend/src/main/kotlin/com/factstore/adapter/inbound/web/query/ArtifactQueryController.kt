package com.factstore.adapter.inbound.web.query

import com.factstore.core.port.inbound.query.IArtifactQueryHandler
import com.factstore.core.port.inbound.IBuildProvenanceService
import com.factstore.dto.BuildProvenanceResponse
import com.factstore.dto.query.ArtifactView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Artifacts – Queries", description = "Artifact read operations (CQRS query path)")
class ArtifactQueryController(
    private val queryHandler: IArtifactQueryHandler,
    private val buildProvenanceService: IBuildProvenanceService
) {

    @GetMapping("/api/v2/trails/{trailId}/artifacts")
    @Operation(summary = "List artifacts for a trail")
    fun listArtifacts(@PathVariable trailId: UUID): ResponseEntity<List<ArtifactView>> =
        ResponseEntity.ok(queryHandler.listArtifactsForTrail(trailId))

    @GetMapping("/api/v2/artifacts")
    @Operation(summary = "Find artifacts by SHA256 digest")
    fun findBySha256(@RequestParam sha256: String): ResponseEntity<List<ArtifactView>> =
        ResponseEntity.ok(queryHandler.findBySha256(sha256))

    @GetMapping("/api/v2/trails/{trailId}/artifacts/{artifactId}/provenance")
    @Operation(summary = "Retrieve build provenance for an artifact")
    fun getProvenance(
        @PathVariable trailId: UUID,
        @PathVariable artifactId: UUID
    ): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.ok(buildProvenanceService.getProvenance(trailId, artifactId))

    @GetMapping("/api/v2/artifacts/{sha256}/provenance")
    @Operation(summary = "Get provenance by artifact SHA256 digest")
    fun getProvenanceBySha256(@PathVariable sha256: String): ResponseEntity<BuildProvenanceResponse> =
        ResponseEntity.ok(buildProvenanceService.getProvenanceBySha256(sha256))
}
