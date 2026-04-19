package com.factstore.core.port.outbound

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

interface ITrailRepository {
    fun save(trail: Trail): Trail
    fun findById(id: UUID): Trail?
    fun findAll(): List<Trail>
    fun existsById(id: UUID): Boolean
    fun findByFlowId(flowId: UUID): List<Trail>
    fun findByFlowId(flowId: UUID, pageable: Pageable): Page<Trail>
    fun searchByQuery(query: String): List<Trail>
    fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant): List<Trail>
    fun findByFlowIdAndCreatedAtAfter(flowId: UUID, from: Instant): List<Trail>
    fun findByFlowIdAndCreatedAtBefore(flowId: UUID, to: Instant): List<Trail>
    fun findByCreatedAtBetween(from: Instant, to: Instant): List<Trail>
    fun findByCreatedAtAfter(from: Instant): List<Trail>
    fun findByCreatedAtBefore(to: Instant): List<Trail>
    fun countAll(): Long
    fun countByStatus(status: TrailStatus): Long
    fun findByFlowIdAndName(flowId: UUID, name: String): Trail?
}
