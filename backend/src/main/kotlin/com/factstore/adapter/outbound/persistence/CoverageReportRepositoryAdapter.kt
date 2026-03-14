package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.CoverageReport
import com.factstore.core.port.outbound.ICoverageReportRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CoverageReportRepositoryJpa : JpaRepository<CoverageReport, UUID> {
    fun findByTrailId(trailId: UUID): List<CoverageReport>
}

@Component
class CoverageReportRepositoryAdapter(private val jpa: CoverageReportRepositoryJpa) : ICoverageReportRepository {
    override fun save(report: CoverageReport): CoverageReport = jpa.save(report)
    override fun findById(id: UUID): CoverageReport? = jpa.findById(id).orElse(null)
    override fun findByTrailId(trailId: UUID): List<CoverageReport> = jpa.findByTrailId(trailId)
}
