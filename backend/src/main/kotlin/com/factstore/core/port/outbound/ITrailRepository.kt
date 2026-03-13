package com.factstore.core.port.outbound

import com.factstore.core.domain.Trail
import java.time.Instant
import java.util.UUID

interface ITrailRepository {
    fun save(trail: Trail): Trail
    fun findById(id: UUID): Trail?
    fun findAll(): List<Trail>
    fun existsById(id: UUID): Boolean
    fun findByFlowId(flowId: UUID): List<Trail>
    fun searchByQuery(query: String): List<Trail>
    fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant): List<Trail>
}
