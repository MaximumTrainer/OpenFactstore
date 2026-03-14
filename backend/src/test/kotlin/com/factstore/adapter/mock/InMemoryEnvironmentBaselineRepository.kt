package com.factstore.adapter.mock

import com.factstore.core.domain.EnvironmentBaseline
import com.factstore.core.port.outbound.IEnvironmentBaselineRepository
import java.util.UUID

class InMemoryEnvironmentBaselineRepository : IEnvironmentBaselineRepository {
    private val store = mutableMapOf<UUID, EnvironmentBaseline>()

    override fun save(baseline: EnvironmentBaseline): EnvironmentBaseline {
        store[baseline.id] = baseline
        return baseline
    }

    override fun findById(id: UUID): EnvironmentBaseline? = store[id]

    override fun findActiveByEnvironmentId(environmentId: UUID): EnvironmentBaseline? =
        store.values.firstOrNull { it.environmentId == environmentId && it.isActive }

    override fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentBaseline> =
        store.values.filter { it.environmentId == environmentId }
}
