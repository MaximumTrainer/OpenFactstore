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
    private val trailRepository: ITrailRepository
) : IDashboardService {

    override fun getStats(): DashboardStatsResponse {
        val flows = flowRepository.findAll()
        val trails = trailRepository.findAll()

        val compliant = trails.count { it.status == TrailStatus.COMPLIANT }
        val nonCompliant = trails.count { it.status == TrailStatus.NON_COMPLIANT }
        val pending = trails.count { it.status == TrailStatus.PENDING }
        val total = trails.size

        val complianceRate = if (total == 0) 0.0
        else Math.round((compliant.toDouble() / total) * 10000.0) / 100.0

        return DashboardStatsResponse(
            totalFlows = flows.size,
            totalTrails = total,
            compliantTrails = compliant,
            nonCompliantTrails = nonCompliant,
            pendingTrails = pending,
            complianceRate = complianceRate
        )
    }
}
