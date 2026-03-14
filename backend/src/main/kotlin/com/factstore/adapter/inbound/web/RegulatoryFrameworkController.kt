package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IRegulatoryComplianceService
import com.factstore.dto.CreateControlRequest
import com.factstore.dto.CreateFrameworkRequest
import com.factstore.dto.ControlResponse
import com.factstore.dto.FrameworkResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/frameworks")
@Tag(name = "Regulatory Frameworks", description = "Manage regulatory compliance frameworks and controls")
class RegulatoryFrameworkController(private val complianceService: IRegulatoryComplianceService) {

    @PostMapping
    @Operation(summary = "Create a regulatory framework")
    fun createFramework(@RequestBody request: CreateFrameworkRequest): ResponseEntity<FrameworkResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createFramework(request))

    @GetMapping
    @Operation(summary = "List all regulatory frameworks")
    fun listFrameworks(): ResponseEntity<List<FrameworkResponse>> =
        ResponseEntity.ok(complianceService.listFrameworks())

    @GetMapping("/{id}")
    @Operation(summary = "Get a framework with its controls")
    fun getFramework(@PathVariable id: UUID): ResponseEntity<FrameworkResponse> =
        ResponseEntity.ok(complianceService.getFramework(id))

    @PostMapping("/{id}/controls")
    @Operation(summary = "Add a control to a framework")
    fun addControl(
        @PathVariable id: UUID,
        @RequestBody request: CreateControlRequest
    ): ResponseEntity<ControlResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(complianceService.addControl(id, request))
}
