package com.factstore.application.query

import com.factstore.application.TenantScope
import com.factstore.core.port.inbound.query.ITrailQueryHandler
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.read.ITrailReadRepository
import com.factstore.dto.query.TrailView
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TrailQueryHandler(
    private val trailReadRepository: ITrailReadRepository,
    private val flowRepository: IFlowRepository,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : ITrailQueryHandler {

    // A trail's organisation is its own, or its flow's (#155 FR-5.2).
    private fun orgOf(trail: TrailView): String? = trail.orgSlug ?: flowRepository.findById(trail.flowId)?.orgSlug

    override fun getTrail(id: UUID): TrailView =
        tenantScope.require(trailReadRepository.findById(id), ::orgOf) { "Trail not found: $id" }

    override fun listTrails(flowId: UUID?): List<TrailView> =
        (if (flowId != null) trailReadRepository.findByFlowId(flowId) else trailReadRepository.findAll())
            .filter { tenantScope.visible(orgOf(it)) }

    override fun listTrailsForFlow(flowId: UUID): List<TrailView> {
        tenantScope.require(flowRepository.findById(flowId), { it.orgSlug }) { "Flow not found: $flowId" }
        return trailReadRepository.findByFlowId(flowId)
    }
}
