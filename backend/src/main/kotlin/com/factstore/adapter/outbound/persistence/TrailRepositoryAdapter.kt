package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Trail
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
}
