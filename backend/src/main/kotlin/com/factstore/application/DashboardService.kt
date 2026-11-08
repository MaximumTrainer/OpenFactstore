package com.factstore.application

import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IDashboardService
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.DashboardStatsResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val flowRepository: IFlowRepository,
    private val trailRepository: ITrailRepository,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : IDashboardService {

    override fun getStats(): DashboardStatsResponse {
        if (tenantScope.orgSlug() != null) return scopedStats()
        val totalFlows = flowRepository.countAll()
        val totalTrails = trailRepository.countAll()
        val compliant = trailRepository.countByStatus(TrailStatus.COMPLIANT)
        val nonCompliant = trailRepository.countByStatus(TrailStatus.NON_COMPLIANT)
        val pending = trailRepository.countByStatus(TrailStatus.PENDING)

        return DashboardStatsResponse(
            totalFlows = totalFlows.toInt(),
            totalTrails = totalTrails.toInt(),
            compliantTrails = compliant.toInt(),
            nonCompliantTrails = nonCompliant.toInt(),
            pendingTrails = pending.toInt(),
            complianceRate = complianceRateOf(compliant.toInt(), totalTrails.toInt())
        )
    }

    /**
     * Aggregates for a caller bound to one organisation count only that organisation (#155
     * FR-5.3). Counted in memory: the unscoped path keeps its database counts.
     */
    private fun scopedStats(): DashboardStatsResponse {
        val flows = flowRepository.findAll().filter { tenantScope.visible(it.orgSlug) }
        val flowOrgs = flows.associate { it.id to it.orgSlug }
        val trails = trailRepository.findAll().filter { tenantScope.visible(it.orgSlug ?: flowOrgs[it.flowId]) }
        val compliant = trails.count { it.status == TrailStatus.COMPLIANT }
        return DashboardStatsResponse(
            totalFlows = flows.size,
            totalTrails = trails.size,
            compliantTrails = compliant,
            nonCompliantTrails = trails.count { it.status == TrailStatus.NON_COMPLIANT },
            pendingTrails = trails.count { it.status == TrailStatus.PENDING },
            complianceRate = complianceRateOf(compliant, trails.size)
        )
    }
}
