package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.GateFallbackReport
import com.factstore.core.port.outbound.IGateFallbackReports
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GateFallbackReportRepository : JpaRepository<GateFallbackReport, UUID>

@Component
class GateFallbackReportsAdapter(private val jpa: GateFallbackReportRepository) : IGateFallbackReports {
    override fun find(reportId: UUID): UUID? = jpa.findById(reportId).orElse(null)?.auditId
    override fun record(reportId: UUID, auditId: UUID) {
        jpa.save(GateFallbackReport(reportId, auditId))
    }
}
