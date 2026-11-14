package com.factstore.core.port.inbound.query

import com.factstore.dto.query.FlowTemplateView
import com.factstore.dto.query.FlowView
import java.util.UUID

interface IFlowQueryHandler {
    fun getFlow(id: UUID): FlowView
    /** Archived flows are left out unless [includeArchived] (#250, as v1). */
    fun listFlows(includeArchived: Boolean = false): List<FlowView>
    fun listFlowsByOrg(orgSlug: String): List<FlowView>
    fun getFlowTemplate(id: UUID): FlowTemplateView
}
