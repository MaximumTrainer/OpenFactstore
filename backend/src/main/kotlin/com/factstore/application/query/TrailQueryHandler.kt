package com.factstore.application.query

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
    private val flowRepository: IFlowRepository
) : ITrailQueryHandler {

    override fun getTrail(id: UUID): TrailView =
        trailReadRepository.findById(id) ?: throw NotFoundException("Trail not found: $id")

    override fun listTrails(flowId: UUID?): List<TrailView> =
        if (flowId != null) trailReadRepository.findByFlowId(flowId)
        else trailReadRepository.findAll()

    override fun listTrailsForFlow(flowId: UUID): List<TrailView> {
        if (!flowRepository.existsById(flowId)) throw NotFoundException("Flow not found: $flowId")
        return trailReadRepository.findByFlowId(flowId)
    }
}
