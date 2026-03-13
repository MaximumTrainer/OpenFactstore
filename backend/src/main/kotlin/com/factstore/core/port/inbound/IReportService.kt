package com.factstore.core.port.inbound

import com.factstore.dto.AuditTrailExportResponse
import com.factstore.dto.FlowComplianceReport
import java.time.Instant
import java.util.UUID

interface IReportService {
    fun getComplianceReport(flowId: UUID?, from: Instant?, to: Instant?): FlowComplianceReport
    fun getAuditTrailExport(trailId: UUID): AuditTrailExportResponse
}
