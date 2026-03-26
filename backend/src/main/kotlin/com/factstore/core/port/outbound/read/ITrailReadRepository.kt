package com.factstore.core.port.outbound.read

import com.factstore.dto.query.TrailView
import java.util.UUID

interface ITrailReadRepository {
    fun findById(id: UUID): TrailView?
    fun findAll(): List<TrailView>
    fun findByFlowId(flowId: UUID): List<TrailView>
}
