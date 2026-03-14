package com.factstore.core.port.outbound

import com.factstore.core.domain.CoverageReport
import java.util.UUID

interface ICoverageReportRepository {
    fun save(report: CoverageReport): CoverageReport
    fun findById(id: UUID): CoverageReport?
    fun findByTrailId(trailId: UUID): List<CoverageReport>
}
