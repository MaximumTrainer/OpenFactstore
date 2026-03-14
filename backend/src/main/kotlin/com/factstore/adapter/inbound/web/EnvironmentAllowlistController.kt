package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IEnvironmentAllowlistService
import com.factstore.dto.AllowlistEntryResponse
import com.factstore.dto.CreateAllowlistEntryRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environment Allowlist", description = "Third-party artifact allow-listing")
class EnvironmentAllowlistController(private val allowlistService: IEnvironmentAllowlistService) {

    @PostMapping("/{id}/allowlist")
    @Operation(summary = "Add an allow-list entry")
    fun addEntry(
        @PathVariable id: UUID,
        @RequestBody request: CreateAllowlistEntryRequest
    ): ResponseEntity<AllowlistEntryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(allowlistService.addEntry(id, request))

    @GetMapping("/{id}/allowlist")
    @Operation(summary = "List allow-list entries")
    fun listEntries(@PathVariable id: UUID): ResponseEntity<List<AllowlistEntryResponse>> =
        ResponseEntity.ok(allowlistService.listEntries(id))

    @DeleteMapping("/{id}/allowlist/{entryId}")
    @Operation(summary = "Remove an allow-list entry")
    fun removeEntry(
        @PathVariable id: UUID,
        @PathVariable entryId: UUID
    ): ResponseEntity<AllowlistEntryResponse> =
        ResponseEntity.ok(allowlistService.removeEntry(id, entryId))
}
