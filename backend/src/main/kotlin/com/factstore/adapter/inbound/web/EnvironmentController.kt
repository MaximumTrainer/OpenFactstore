package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.EnvironmentSnapshotResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.UpdateEnvironmentRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "Environment tracking and snapshot management")
class EnvironmentController(private val environmentService: IEnvironmentService) {

    @PostMapping
    @Operation(summary = "Register a new environment")
    fun createEnvironment(@RequestBody request: CreateEnvironmentRequest): ResponseEntity<EnvironmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(environmentService.createEnvironment(request))

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
        @RequestBody request: RecordSnapshotRequest
    ): ResponseEntity<EnvironmentSnapshotResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(environmentService.recordSnapshot(id, request))

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
}
