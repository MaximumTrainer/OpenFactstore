package com.factstore.core.port.inbound.query

import com.factstore.dto.query.TrailView
import java.util.UUID

interface ITrailQueryHandler {
    fun getTrail(id: UUID): TrailView
    /** Archived trails are left out unless [includeArchived] (#250, as v1). */
    fun listTrails(flowId: UUID?, includeArchived: Boolean = false): List<TrailView>
    fun listTrailsForFlow(flowId: UUID, includeArchived: Boolean = false): List<TrailView>
}
