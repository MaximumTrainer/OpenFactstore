package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.IRegulatoryComplianceService
import com.factstore.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Regulatory Compliance", description = "Compliance mappings, assessments, and regulatory reports")
class RegulatoryComplianceController(private val complianceService: IRegulatoryComplianceService) {

    @PreAuthorize("@authz.has('policies:write')")
    @PostMapping("/api/v1/compliance/mappings")
    @Operation(summary = "Create a compliance mapping")
    fun createMapping(@RequestBody request: CreateMappingRequest): ResponseEntity<MappingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createMapping(request))

    @PreAuthorize("@authz.has('policies:read')")
    @GetMapping("/api/v1/compliance/mappings")
    @Operation(summary = "List all compliance mappings")
    fun listMappings(): ResponseEntity<List<MappingResponse>> =
        ResponseEntity.ok(complianceService.listMappings())

    @PreAuthorize("@authz.has('assert:execute')")
    @PostMapping("/api/v1/compliance/assess")
    @Operation(summary = "Run a compliance assessment for a trail against a framework")
    fun assessTrail(@RequestBody request: AssessTrailRequest): ResponseEntity<AssessmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(complianceService.assessTrail(request))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/compliance/assessments")
    @Operation(summary = "List all compliance assessments")
    fun listAssessments(): ResponseEntity<List<AssessmentResponse>> =
        ResponseEntity.ok(complianceService.listAssessments())

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/compliance/assessments/{id}")
    @Operation(summary = "Get a compliance assessment by ID")
    fun getAssessment(@PathVariable id: UUID): ResponseEntity<AssessmentResponse> =
        ResponseEntity.ok(complianceService.getAssessment(id))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/reports/regulatory/{frameworkId}")
    @Operation(summary = "Generate a regulatory report for a framework")
    fun generateReport(@PathVariable frameworkId: UUID): ResponseEntity<RegulatoryReportResponse> =
        ResponseEntity.ok(complianceService.generateReport(frameworkId))
}
