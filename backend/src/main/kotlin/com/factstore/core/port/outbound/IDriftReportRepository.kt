package com.factstore.core.port.outbound

import com.factstore.core.domain.DriftReport
import java.util.UUID

interface IDriftReportRepository {
    fun save(report: DriftReport): DriftReport
    fun findAllByEnvironmentId(environmentId: UUID): List<DriftReport>
    fun findLatestByEnvironmentId(environmentId: UUID): DriftReport?
    fun countByHasDrift(hasDrift: Boolean): Long
}
