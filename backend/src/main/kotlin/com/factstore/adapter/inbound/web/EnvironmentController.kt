package com.factstore.adapter.inbound.web

import com.factstore.application.DryRunContext
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.dto.BaselineResponse
import com.factstore.dto.CreateBaselineRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.DriftReportResponse
import com.factstore.dto.DryRunResponse
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.EnvironmentSnapshotResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactResponse
import com.factstore.dto.SnapshotDiffResponse
import com.factstore.dto.UpdateEnvironmentRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "Environment tracking and snapshot management")
class EnvironmentController(private val environmentService: IEnvironmentService) {

    @PostMapping
    @Operation(summary = "Register a new environment")
    fun createEnvironment(
        @RequestBody request: CreateEnvironmentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val now = Instant.now()
            val wouldBe = EnvironmentResponse(
                id = UUID.randomUUID(),
                name = request.name,
                type = request.type,
                description = request.description,
                orgSlug = request.orgSlug,
                driftPolicy = request.driftPolicy,
                scope = request.scope,
                createdAt = now,
                updatedAt = now
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentService.createEnvironment(request))
    }

    @GetMapping
    @Operation(summary = "List all environments")
    fun listEnvironments(): ResponseEntity<List<EnvironmentResponse>> =
        ResponseEntity.ok(environmentService.listEnvironments())

    @GetMapping("/{id}")
    @Operation(summary = "Get environment by ID")
    fun getEnvironment(@PathVariable id: UUID): ResponseEntity<EnvironmentResponse> =
        ResponseEntity.ok(environmentService.getEnvironment(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update an environment")
    fun updateEnvironment(
        @PathVariable id: UUID,
        @RequestBody request: UpdateEnvironmentRequest
    ): ResponseEntity<EnvironmentResponse> =
        ResponseEntity.ok(environmentService.updateEnvironment(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an environment")
    fun deleteEnvironment(@PathVariable id: UUID): ResponseEntity<Void> {
        environmentService.deleteEnvironment(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/snapshots")
    @Operation(summary = "Record a new snapshot for an environment")
    fun recordSnapshot(
        @PathVariable id: UUID,
        @RequestBody request: RecordSnapshotRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val wouldBe = EnvironmentSnapshotResponse(
                id = UUID.randomUUID(),
                environmentId = id,
                snapshotIndex = 0L,
                recordedAt = Instant.now(),
                recordedBy = request.recordedBy,
                artifacts = request.artifacts.map { a ->
                    SnapshotArtifactResponse(
                        artifactSha256 = a.artifactSha256,
                        artifactName = a.artifactName,
                        artifactTag = a.artifactTag,
                        instanceCount = a.instanceCount
                    )
                }
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentService.recordSnapshot(id, request))
    }

    @GetMapping("/{id}/snapshots")
    @Operation(summary = "List all snapshots for an environment")
    fun listSnapshots(@PathVariable id: UUID): ResponseEntity<List<EnvironmentSnapshotResponse>> =
        ResponseEntity.ok(environmentService.listSnapshots(id))

    @GetMapping("/{id}/snapshots/latest")
    @Operation(summary = "Get the most recent snapshot for an environment")
    fun getLatestSnapshot(@PathVariable id: UUID): ResponseEntity<EnvironmentSnapshotResponse> =
        ResponseEntity.ok(environmentService.getLatestSnapshot(id))

    @GetMapping("/{id}/snapshots/{snapshotIndex}")
    @Operation(summary = "Get a specific snapshot by index")
    fun getSnapshot(
        @PathVariable id: UUID,
        @PathVariable snapshotIndex: Long
    ): ResponseEntity<EnvironmentSnapshotResponse> =
        ResponseEntity.ok(environmentService.getSnapshot(id, snapshotIndex))

    @GetMapping("/{id}/diff")
    @Operation(summary = "Diff two snapshots of an environment")
    fun diffSnapshots(
        @PathVariable id: UUID,
        @RequestParam from: Long,
        @RequestParam to: Long
    ): ResponseEntity<SnapshotDiffResponse> =
        ResponseEntity.ok(environmentService.diffSnapshots(id, from, to))

    @PostMapping("/{id}/baselines")
    @Operation(summary = "Create a new baseline for an environment")
    fun createBaseline(
        @PathVariable id: UUID,
        @RequestBody request: CreateBaselineRequest
    ): ResponseEntity<BaselineResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(environmentService.createBaseline(id, request))

    @GetMapping("/{id}/baselines/current")
    @Operation(summary = "Get the current active baseline for an environment")
    fun getCurrentBaseline(@PathVariable id: UUID): ResponseEntity<BaselineResponse> =
        ResponseEntity.ok(environmentService.getCurrentBaseline(id))

    @GetMapping("/{id}/drift")
    @Operation(summary = "Check drift between current snapshot and baseline")
    fun checkDrift(@PathVariable id: UUID): ResponseEntity<DriftReportResponse> =
        ResponseEntity.ok(environmentService.checkDrift(id))

    @GetMapping("/{id}/drift/history")
    @Operation(summary = "List all drift reports for an environment")
    fun listDriftHistory(@PathVariable id: UUID): ResponseEntity<List<DriftReportResponse>> =
        ResponseEntity.ok(environmentService.listDriftHistory(id))
}

