package com.factstore.core.port.inbound.query

import com.factstore.dto.query.TrailView
import java.util.UUID

interface ITrailQueryHandler {
    fun getTrail(id: UUID): TrailView
    fun listTrails(flowId: UUID?): List<TrailView>
    fun listTrailsForFlow(flowId: UUID): List<TrailView>
}
