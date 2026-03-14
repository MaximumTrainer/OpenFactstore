package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ILogicalEnvironmentService
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.MergedSnapshotResponse
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/logical-environments")
@Tag(name = "Logical Environments", description = "Logical environment grouping and merged snapshot management")
class LogicalEnvironmentController(private val logicalEnvironmentService: ILogicalEnvironmentService) {

    @PostMapping
    @Operation(summary = "Create a logical environment")
    fun createLogicalEnvironment(@RequestBody request: CreateLogicalEnvironmentRequest): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(logicalEnvironmentService.createLogicalEnvironment(request))

    @GetMapping
    @Operation(summary = "List all logical environments")
    fun listLogicalEnvironments(): ResponseEntity<List<LogicalEnvironmentResponse>> =
        ResponseEntity.ok(logicalEnvironmentService.listLogicalEnvironments())

    @GetMapping("/{id}")
    @Operation(summary = "Get logical environment by ID")
    fun getLogicalEnvironment(@PathVariable id: UUID): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.ok(logicalEnvironmentService.getLogicalEnvironment(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update a logical environment")
    fun updateLogicalEnvironment(
        @PathVariable id: UUID,
        @RequestBody request: UpdateLogicalEnvironmentRequest
    ): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.ok(logicalEnvironmentService.updateLogicalEnvironment(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a logical environment")
    fun deleteLogicalEnvironment(@PathVariable id: UUID): ResponseEntity<Void> {
        logicalEnvironmentService.deleteLogicalEnvironment(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/members/{physicalEnvId}")
    @Operation(summary = "Add a physical environment to a logical environment")
    fun addMember(
        @PathVariable id: UUID,
        @PathVariable physicalEnvId: UUID
    ): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.ok(logicalEnvironmentService.addMember(id, physicalEnvId))

    @DeleteMapping("/{id}/members/{physicalEnvId}")
    @Operation(summary = "Remove a physical environment from a logical environment")
    fun removeMember(
        @PathVariable id: UUID,
        @PathVariable physicalEnvId: UUID
    ): ResponseEntity<Void> {
        logicalEnvironmentService.removeMember(id, physicalEnvId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/snapshots/latest")
    @Operation(summary = "Get the latest merged snapshot for a logical environment")
    fun getMergedSnapshot(@PathVariable id: UUID): ResponseEntity<MergedSnapshotResponse> =
        ResponseEntity.ok(logicalEnvironmentService.getMergedSnapshot(id))
}
