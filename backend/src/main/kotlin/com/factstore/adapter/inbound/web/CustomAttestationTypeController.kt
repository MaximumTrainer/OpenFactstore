package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.ICustomAttestationTypeService
import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.CustomAttestationTypeResponse
import com.factstore.dto.UpdateCustomAttestationTypeRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/attestation-types")
@Tag(name = "Custom Attestation Types", description = "Custom attestation type registry")
class CustomAttestationTypeController(
    private val service: ICustomAttestationTypeService
) {
    @PreAuthorize("@authz.has('flows:write')")
    @PostMapping
    @Operation(summary = "Create a custom attestation type")
    fun create(@RequestBody request: CreateCustomAttestationTypeRequest): ResponseEntity<CustomAttestationTypeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.createType(request))

    @PreAuthorize("@authz.has('flows:read')")
    @GetMapping
    @Operation(summary = "List custom attestation types")
    fun list(@RequestParam(defaultValue = "false") includeArchived: Boolean): ResponseEntity<List<CustomAttestationTypeResponse>> =
        ResponseEntity.ok(service.listTypes(includeArchived))

    @PreAuthorize("@authz.has('flows:read')")
    @GetMapping("/{id}")
    @Operation(summary = "Get custom attestation type by ID")
    fun get(@PathVariable id: UUID): ResponseEntity<CustomAttestationTypeResponse> =
        ResponseEntity.ok(service.getType(id))

    @PreAuthorize("@authz.has('flows:write')")
    @PutMapping("/{id}")
    @Operation(summary = "Update a custom attestation type")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateCustomAttestationTypeRequest): ResponseEntity<CustomAttestationTypeResponse> =
        ResponseEntity.ok(service.updateType(id, request))

    @PreAuthorize("@authz.has('flows:write')")
    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a custom attestation type")
    fun archive(@PathVariable id: UUID): ResponseEntity<CustomAttestationTypeResponse> =
        ResponseEntity.ok(service.archiveType(id))

    @PreAuthorize("@authz.has('flows:write')")
    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Unarchive a custom attestation type")
    fun unarchive(@PathVariable id: UUID): ResponseEntity<CustomAttestationTypeResponse> =
        ResponseEntity.ok(service.unarchiveType(id))
}
