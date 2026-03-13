package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.outbound.ITrailRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TrailRepositoryJpa : JpaRepository<Trail, UUID> {
    fun findByFlowId(flowId: UUID): List<Trail>
    fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant): List<Trail>
    fun findByFlowIdAndCreatedAtGreaterThanEqual(flowId: UUID, from: Instant): List<Trail>
    fun findByFlowIdAndCreatedAtLessThanEqual(flowId: UUID, to: Instant): List<Trail>
    fun findByCreatedAtBetween(from: Instant, to: Instant): List<Trail>
    fun findByCreatedAtGreaterThanEqual(from: Instant): List<Trail>
    fun findByCreatedAtLessThanEqual(to: Instant): List<Trail>
    fun countByStatus(status: TrailStatus): Long

    @Query("""
        SELECT t FROM Trail t WHERE
        LOWER(t.gitCommitSha) LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(t.gitBranch)    LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(t.gitAuthor)    LIKE LOWER(CONCAT('%', :query, '%')) OR
        LOWER(t.gitAuthorEmail) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    fun searchByQuery(@Param("query") query: String): List<Trail>
}

@Component
class TrailRepositoryAdapter(private val jpa: TrailRepositoryJpa) : ITrailRepository {
    override fun save(trail: Trail): Trail = jpa.save(trail)
    override fun findById(id: UUID): Trail? = jpa.findById(id).orElse(null)
    override fun findAll(): List<Trail> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun findByFlowId(flowId: UUID): List<Trail> = jpa.findByFlowId(flowId)
    override fun searchByQuery(query: String): List<Trail> = jpa.searchByQuery(query)
    override fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant): List<Trail> =
        jpa.findByFlowIdAndCreatedAtBetween(flowId, from, to)
    override fun findByFlowIdAndCreatedAtAfter(flowId: UUID, from: Instant): List<Trail> =
        jpa.findByFlowIdAndCreatedAtGreaterThanEqual(flowId, from)
    override fun findByFlowIdAndCreatedAtBefore(flowId: UUID, to: Instant): List<Trail> =
        jpa.findByFlowIdAndCreatedAtLessThanEqual(flowId, to)
    override fun findByCreatedAtBetween(from: Instant, to: Instant): List<Trail> =
        jpa.findByCreatedAtBetween(from, to)
    override fun findByCreatedAtAfter(from: Instant): List<Trail> =
        jpa.findByCreatedAtGreaterThanEqual(from)
    override fun findByCreatedAtBefore(to: Instant): List<Trail> =
        jpa.findByCreatedAtLessThanEqual(to)
    override fun countAll(): Long = jpa.count()
    override fun countByStatus(status: TrailStatus): Long = jpa.countByStatus(status)
}
