package com.factstore.core.port.inbound

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.PageResponse
import com.factstore.dto.TrailResponse
import java.util.UUID

interface ITrailService {
    fun createTrail(request: CreateTrailRequest): TrailResponse
    fun listTrails(flowId: UUID?): List<TrailResponse>
    fun getTrail(id: UUID): TrailResponse
    fun listTrailsForFlow(flowId: UUID): List<TrailResponse>
    fun listTrailsForFlow(flowId: UUID, page: Int, size: Int): PageResponse<TrailResponse>
    fun updateTrailStatus(id: UUID, status: TrailStatus): Trail
    fun getTrailEntity(id: UUID): Trail
    fun findByName(flowId: UUID, name: String): TrailResponse
}
