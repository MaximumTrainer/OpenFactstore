package com.factstore.adapter.mock

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.outbound.ITrailRepository
import java.time.Instant
import java.util.UUID

/**
 * In-memory implementation of ITrailRepository for use in unit tests.
 */
class InMemoryTrailRepository : ITrailRepository {
    private val store = mutableMapOf<UUID, Trail>()

    override fun save(trail: Trail): Trail {
        store[trail.id] = trail
        return trail
    }

    override fun findById(id: UUID): Trail? = store[id]

    override fun findAll(): List<Trail> = store.values.toList()

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun findByFlowId(flowId: UUID): List<Trail> =
        store.values.filter { it.flowId == flowId }

    override fun searchByQuery(query: String): List<Trail> =
        store.values.filter { t ->
            t.gitCommitSha.contains(query, ignoreCase = true) ||
                t.gitBranch.contains(query, ignoreCase = true) ||
                t.gitAuthor.contains(query, ignoreCase = true)
        }

    override fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant): List<Trail> =
        store.values.filter { it.flowId == flowId && it.createdAt >= from && it.createdAt <= to }

    override fun findByFlowIdAndCreatedAtAfter(flowId: UUID, from: Instant): List<Trail> =
        store.values.filter { it.flowId == flowId && it.createdAt >= from }

    override fun findByFlowIdAndCreatedAtBefore(flowId: UUID, to: Instant): List<Trail> =
        store.values.filter { it.flowId == flowId && it.createdAt <= to }

    override fun findByCreatedAtBetween(from: Instant, to: Instant): List<Trail> =
        store.values.filter { it.createdAt >= from && it.createdAt <= to }

    override fun findByCreatedAtAfter(from: Instant): List<Trail> =
        store.values.filter { it.createdAt >= from }

    override fun findByCreatedAtBefore(to: Instant): List<Trail> =
        store.values.filter { it.createdAt <= to }

    override fun countAll(): Long = store.size.toLong()

    override fun countByStatus(status: TrailStatus): Long =
        store.values.count { it.status == status }.toLong()
}
