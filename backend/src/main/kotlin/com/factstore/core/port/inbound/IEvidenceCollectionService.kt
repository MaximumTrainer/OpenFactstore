package com.factstore.core.port.inbound

import com.factstore.dto.BulkEvidenceRequest
import com.factstore.dto.BulkEvidenceResponse
import com.factstore.dto.CoverageReportResponse
import com.factstore.dto.EvidenceGapsResponse
import com.factstore.dto.EvidenceSummaryResponse
import com.factstore.dto.ReportCoverageRequest
import java.util.UUID

interface IEvidenceCollectionService {
    fun reportCoverage(trailId: UUID, request: ReportCoverageRequest): CoverageReportResponse
    fun getCoverageReports(trailId: UUID): List<CoverageReportResponse>
    fun collectBulkEvidence(request: BulkEvidenceRequest): BulkEvidenceResponse
    fun getEvidenceSummary(trailId: UUID): EvidenceSummaryResponse
    fun getEvidenceGaps(): EvidenceGapsResponse
}
