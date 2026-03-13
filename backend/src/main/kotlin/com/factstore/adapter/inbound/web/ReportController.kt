package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IReportService
import com.factstore.dto.AuditTrailExportResponse
import com.factstore.dto.FlowComplianceReport
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Compliance reports and audit trail export")
class ReportController(private val reportService: IReportService) {

    @GetMapping("/compliance")
    @Operation(
        summary = "Per-flow compliance summary report",
        description = "Returns compliance stats for a flow. Optionally filter by date range (ISO-8601). Omit flowId for an aggregate report across all flows."
    )
    fun getComplianceReport(
        @RequestParam(required = false) flowId: UUID?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?
    ): ResponseEntity<FlowComplianceReport> =
        ResponseEntity.ok(reportService.getComplianceReport(flowId, from, to))

    @GetMapping("/audit-trail/{trailId}")
    @Operation(
        summary = "Full audit trail export",
        description = "Returns the complete audit trail including flow definition, artifacts, attestations, and evidence files. Suitable for submission to external audit systems."
    )
    fun getAuditTrailExport(@PathVariable trailId: UUID): ResponseEntity<AuditTrailExportResponse> =
        ResponseEntity.ok(reportService.getAuditTrailExport(trailId))
}
