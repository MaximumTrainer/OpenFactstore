package com.factstore.adapter.mock

import com.factstore.core.domain.Flow
import com.factstore.core.port.outbound.IFlowRepository
import java.util.UUID

/**
 * In-memory implementation of IFlowRepository for use in unit tests.
 * Demonstrates how the domain can be tested in isolation without a database.
 */
class InMemoryFlowRepository : IFlowRepository {
    private val store = mutableMapOf<UUID, Flow>()

    override fun save(flow: Flow): Flow {
        store[flow.id] = flow
        return flow
    }

    override fun findById(id: UUID): Flow? = store[id]

    override fun findAll(): List<Flow> = store.values.toList()

    override fun findAllByIds(ids: Collection<UUID>): List<Flow> = ids.mapNotNull { store[it] }

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun existsByName(name: String): Boolean = store.values.any { it.name == name }

    override fun deleteById(id: UUID) {
        store.remove(id)
    }

    override fun countAll(): Long = store.size.toLong()

    override fun findAllByOrgSlug(orgSlug: String): List<Flow> =
        store.values.filter { it.orgSlug == orgSlug }
}
