package com.factstore.adapter.mock

import com.factstore.core.domain.RegulatoryFramework
import com.factstore.core.port.outbound.IRegulatoryFrameworkRepository
import java.util.UUID

class InMemoryRegulatoryFrameworkRepository : IRegulatoryFrameworkRepository {
    private val store = mutableMapOf<UUID, RegulatoryFramework>()
    override fun save(framework: RegulatoryFramework): RegulatoryFramework { store[framework.id] = framework; return framework }
    override fun findById(id: UUID): RegulatoryFramework? = store[id]
    override fun findAll(): List<RegulatoryFramework> = store.values.toList()
    override fun findActive(): List<RegulatoryFramework> = store.values.filter { it.isActive }
    override fun existsByName(name: String): Boolean = store.values.any { it.name == name }
}
