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
}
