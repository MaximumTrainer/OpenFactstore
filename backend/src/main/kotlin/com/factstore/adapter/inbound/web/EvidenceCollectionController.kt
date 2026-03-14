package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IEvidenceCollectionService
import com.factstore.dto.BulkEvidenceRequest
import com.factstore.dto.BulkEvidenceResponse
import com.factstore.dto.CoverageReportResponse
import com.factstore.dto.EvidenceGapsResponse
import com.factstore.dto.EvidenceSummaryResponse
import com.factstore.dto.ReportCoverageRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Evidence Collection", description = "Continuous evidence collection pipeline endpoints")
class EvidenceCollectionController(
    private val evidenceCollectionService: IEvidenceCollectionService
) {

    @PostMapping("/trails/{trailId}/coverage")
    @Operation(summary = "Report test coverage for a trail")
    fun reportCoverage(
        @PathVariable trailId: UUID,
        @RequestBody request: ReportCoverageRequest
    ): ResponseEntity<CoverageReportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(evidenceCollectionService.reportCoverage(trailId, request))

    @GetMapping("/trails/{trailId}/coverage")
    @Operation(summary = "Get test coverage reports for a trail")
    fun getCoverageReports(@PathVariable trailId: UUID): ResponseEntity<List<CoverageReportResponse>> =
        ResponseEntity.ok(evidenceCollectionService.getCoverageReports(trailId))

    @PostMapping("/evidence/collect")
    @Operation(summary = "Bulk evidence collection endpoint for CI/CD pipelines")
    fun collectBulkEvidence(@RequestBody request: BulkEvidenceRequest): ResponseEntity<BulkEvidenceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(evidenceCollectionService.collectBulkEvidence(request))

    @GetMapping("/trails/{trailId}/evidence-summary")
    @Operation(summary = "Get evidence summary for a trail")
    fun getEvidenceSummary(@PathVariable trailId: UUID): ResponseEntity<EvidenceSummaryResponse> =
        ResponseEntity.ok(evidenceCollectionService.getEvidenceSummary(trailId))

    @GetMapping("/evidence/gaps")
    @Operation(summary = "Identify trails with missing required evidence")
    fun getEvidenceGaps(): ResponseEntity<EvidenceGapsResponse> =
        ResponseEntity.ok(evidenceCollectionService.getEvidenceGaps())
}
