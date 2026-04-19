package com.factstore.core.port.outbound.read

import com.factstore.dto.query.FlowView
import java.util.UUID

interface IFlowReadRepository {
    fun findById(id: UUID): FlowView?
    fun findAll(): List<FlowView>
    fun findAllByOrgSlug(orgSlug: String): List<FlowView>
}
