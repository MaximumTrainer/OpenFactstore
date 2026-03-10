package com.factstore.controller

import com.factstore.dto.ArtifactResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.service.ArtifactService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Artifacts", description = "Artifact management")
class ArtifactController(private val artifactService: ArtifactService) {

    @PostMapping("/api/v1/trails/{trailId}/artifacts")
    @Operation(summary = "Report an artifact for a trail")
    fun reportArtifact(
        @PathVariable trailId: UUID,
        @RequestBody request: CreateArtifactRequest
    ): ResponseEntity<ArtifactResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(artifactService.reportArtifact(trailId, request))

    @GetMapping("/api/v1/trails/{trailId}/artifacts")
    @Operation(summary = "List artifacts for a trail")
    fun listArtifacts(@PathVariable trailId: UUID): ResponseEntity<List<ArtifactResponse>> =
        ResponseEntity.ok(artifactService.listArtifactsForTrail(trailId))

    @GetMapping("/api/v1/artifacts")
    @Operation(summary = "Find artifacts by SHA256 digest")
    fun findBySha256(@RequestParam sha256: String): ResponseEntity<List<ArtifactResponse>> =
        ResponseEntity.ok(artifactService.findBySha256(sha256))
}
