package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.DriftReport
import com.factstore.core.port.outbound.IDriftReportRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DriftReportRepositoryJpa : JpaRepository<DriftReport, UUID> {
    fun findAllByEnvironmentId(environmentId: UUID): List<DriftReport>
    fun findFirstByEnvironmentIdOrderByGeneratedAtDesc(environmentId: UUID): DriftReport?
    fun countByHasDrift(hasDrift: Boolean): Long
}

@Component
class DriftReportRepositoryAdapter(
    private val jpa: DriftReportRepositoryJpa
) : IDriftReportRepository {
    override fun save(report: DriftReport): DriftReport = jpa.save(report)
    override fun findAllByEnvironmentId(environmentId: UUID): List<DriftReport> =
        jpa.findAllByEnvironmentId(environmentId)
    override fun findLatestByEnvironmentId(environmentId: UUID): DriftReport? =
        jpa.findFirstByEnvironmentIdOrderByGeneratedAtDesc(environmentId)
    override fun countByHasDrift(hasDrift: Boolean): Long = jpa.countByHasDrift(hasDrift)
}
