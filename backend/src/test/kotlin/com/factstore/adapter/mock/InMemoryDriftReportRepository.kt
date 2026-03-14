package com.factstore.adapter.mock

import com.factstore.core.domain.DriftReport
import com.factstore.core.port.outbound.IDriftReportRepository
import java.util.UUID

class InMemoryDriftReportRepository : IDriftReportRepository {
    private val store = mutableMapOf<UUID, DriftReport>()

    override fun save(report: DriftReport): DriftReport {
        store[report.id] = report
        return report
    }

    override fun findAllByEnvironmentId(environmentId: UUID): List<DriftReport> =
        store.values.filter { it.environmentId == environmentId }

    override fun findLatestByEnvironmentId(environmentId: UUID): DriftReport? =
        store.values.filter { it.environmentId == environmentId }
            .maxByOrNull { it.generatedAt }
}
